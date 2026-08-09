from unittest.mock import MagicMock

import pytest

from guardar_enlaces.almacen_local import AlmacenLocal
from guardar_enlaces.modelo import nuevo_elemento_local
from guardar_enlaces.sincronizador import Sincronizador


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
