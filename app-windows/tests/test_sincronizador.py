import time
from unittest.mock import MagicMock

import pytest

from guardar_enlaces.almacen_local import AlmacenLocal
from guardar_enlaces.modelo import nueva_etiqueta_definida, nuevo_elemento_local
from guardar_enlaces.sincronizador import Sincronizador, aviso_tras_sincronizar, toca_sincronizar


@pytest.fixture
def almacen():
    a = AlmacenLocal(":memory:")
    yield a
    a.cerrar()


@pytest.fixture
def cliente():
    return MagicMock()


@pytest.fixture
def sesion():
    s = MagicMock()
    s.con_reintento.side_effect = lambda funcion: funcion("token-de-prueba")
    return s


def test_pull_vacio_no_sube_nada_y_fija_el_cursor(almacen, cliente, sesion):
    cliente.pull.return_value = {"elementos": [], "servidorEn": 12345, "masDisponible": False}

    Sincronizador(almacen, cliente, sesion).sincronizar()

    cliente.push.assert_not_called()
    assert almacen.cursor() == 12345


def test_baja_elementos_nuevos_del_pull(almacen, cliente, sesion):
    nuevo = {
        "id": "e1", "url": "https://a.com", "titulo": "A",
        "actualizadoEn": 100, "creadoEn": 100, "borrado": False,
    }
    cliente.pull.return_value = {"elementos": [nuevo], "servidorEn": 200, "masDisponible": False}

    Sincronizador(almacen, cliente, sesion).sincronizar()

    assert almacen.cargar_todos()["e1"].titulo == "A"
    assert almacen.cursor() == 200


def test_pagina_el_pull_mientras_masDisponible_sea_true(almacen, cliente, sesion):
    pagina1 = {
        "elementos": [{"id": "e1", "url": "https://a.com", "actualizadoEn": 100, "creadoEn": 100, "borrado": False}],
        "servidorEn": 999,
        "masDisponible": True,
    }
    pagina2 = {
        "elementos": [{"id": "e2", "url": "https://b.com", "actualizadoEn": 200, "creadoEn": 200, "borrado": False}],
        "servidorEn": 999,
        "masDisponible": False,
    }
    cliente.pull.side_effect = [pagina1, pagina2]

    Sincronizador(almacen, cliente, sesion).sincronizar()

    assert set(almacen.cargar_todos().keys()) == {"e1", "e2"}
    assert cliente.pull.call_count == 2
    # la segunda llamada pide "desde" = el actualizadoEn de la primera pagina, no 0
    assert cliente.pull.call_args_list[1].args[0] == 100


def test_sube_lo_pendiente_antes_de_bajar_y_limpia_el_outbox(almacen, cliente, sesion):
    local = nuevo_elemento_local("https://mio.com", titulo="Mio", ahora=lambda: 50)
    almacen.marcar_pendiente(local)

    definitivo = {**local.to_json_dict(), "actualizadoEn": 50}
    cliente.push.return_value = {"elementos": [definitivo]}
    cliente.pull.return_value = {"elementos": [], "servidorEn": 500, "masDisponible": False}

    Sincronizador(almacen, cliente, sesion).sincronizar()

    cliente.push.assert_called_once()
    assert almacen.cargar_pendientes() == {}
    assert almacen.cargar_todos()[local.id].titulo == "Mio"


def test_lo_rechazado_tambien_sale_del_outbox(almacen, cliente, sesion):
    # El servidor no lo aplica y no lo devuelve en "elementos". Si solo se limpiara
    # con esos, el elemento se reenviaria en cada sincronizacion para siempre.
    local = nuevo_elemento_local("https://mio.com", titulo="Mio", ahora=lambda: 50)
    almacen.marcar_pendiente(local)

    cliente.push.return_value = {
        "elementos": [],
        "rechazados": [{"id": local.id, "motivo": "no_aplicable"}],
    }
    cliente.pull.return_value = {"elementos": [], "servidorEn": 500, "masDisponible": False}

    rechazados = Sincronizador(almacen, cliente, sesion).sincronizar()

    assert rechazados == 1
    assert almacen.cargar_pendientes() == {}
    # El enlace no se pierde de la cache local, solo deja de reintentarse.
    assert almacen.cargar_todos()[local.id] is not None


def test_baja_etiquetas_reservadas_nuevas_del_pull(almacen, cliente, sesion):
    nueva = {"id": "t1", "nombre": "ocio", "actualizadoEn": 100, "creadoEn": 100, "borrado": False}
    cliente.pull.return_value = {
        "elementos": [],
        "etiquetasDefinidas": [nueva],
        "servidorEn": 200,
        "masDisponible": False,
    }

    Sincronizador(almacen, cliente, sesion).sincronizar()

    assert almacen.cargar_etiquetas_definidas()["t1"].nombre == "ocio"


def test_sube_una_etiqueta_pendiente_en_el_mismo_push_que_los_elementos(almacen, cliente, sesion):
    etiqueta = nueva_etiqueta_definida("ocio", ahora=lambda: 50)
    almacen.marcar_etiqueta_pendiente(etiqueta)

    cliente.push.return_value = {
        "elementos": [],
        "rechazados": [],
        "etiquetasDefinidas": [etiqueta.to_json_dict()],
        "etiquetasRechazadas": [],
    }
    cliente.pull.return_value = {"elementos": [], "servidorEn": 500, "masDisponible": False}

    Sincronizador(almacen, cliente, sesion).sincronizar()

    cliente.push.assert_called_once()
    _, kwargs = cliente.push.call_args
    assert kwargs["etiquetas_definidas"] == [etiqueta.to_json_dict()]
    assert almacen.cargar_etiquetas_pendientes() == {}
    assert almacen.cargar_etiquetas_definidas()[etiqueta.id].nombre == "ocio"


def test_una_etiqueta_rechazada_tambien_sale_del_outbox_y_cuenta(almacen, cliente, sesion):
    etiqueta = nueva_etiqueta_definida("ocio", ahora=lambda: 50)
    almacen.marcar_etiqueta_pendiente(etiqueta)

    cliente.push.return_value = {
        "elementos": [],
        "rechazados": [],
        "etiquetasDefinidas": [],
        "etiquetasRechazadas": [{"id": etiqueta.id, "motivo": "no_aplicable"}],
    }
    cliente.pull.return_value = {"elementos": [], "servidorEn": 500, "masDisponible": False}

    rechazados = Sincronizador(almacen, cliente, sesion).sincronizar()

    assert rechazados == 1
    assert almacen.cargar_etiquetas_pendientes() == {}


class TestTocaSincronizar:
    def test_sin_ninguna_sincronizacion_previa_toca(self):
        # El cero no es "hace un instante": con un reloj de verdad cualquier
        # momento actual esta muy por encima del intervalo.
        assert toca_sincronizar(0.0, time.monotonic())

    def test_volver_dos_veces_seguidas_no_lanza_dos(self):
        assert not toca_sincronizar(1000.0, 1001.0, 30.0)

    def test_pasado_el_intervalo_vuelve_a_tocar(self):
        assert toca_sincronizar(1000.0, 1031.0, 30.0)


class TestAvisoTrasSincronizar:
    def test_la_pedida_confirma(self):
        # F5 no decia nada: no se sabia si habia hecho algo.
        assert aviso_tras_sincronizar(0, manual=True) == "Sincronizado"

    def test_la_automatica_calla_si_todo_fue_bien(self):
        assert aviso_tras_sincronizar(0, manual=False) == ""

    def test_los_rechazos_se_dicen_siempre_y_concuerdan(self):
        assert aviso_tras_sincronizar(1, manual=False) == "1 cambio no se pudo subir al servidor"
        assert aviso_tras_sincronizar(3, manual=True) == "3 cambios no se pudieron subir al servidor"
