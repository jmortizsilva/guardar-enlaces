"""Sondeo del login con Google. El reloj y la espera se inyectan: estas
pruebas no duermen ni un segundo de verdad."""

from unittest.mock import MagicMock

from guardar_enlaces.api_cliente import ErrorApi
from guardar_enlaces.login_oauth import (
    generar_estado,
    esperar_codigo_canje,
    mensaje_de_error,
)


class RelojFalso:
    """Avanza solo cuando se "duerme", asi el limite de tiempo se agota en
    cuanto se han dado las vueltas suficientes."""

    def __init__(self):
        self.segundos = 0.0

    def ahora(self) -> float:
        return self.segundos

    def dormir(self, cuanto: float) -> None:
        self.segundos += cuanto


def _esperar(cliente, cancelado=lambda: False, limite_s=10.0):
    reloj = RelojFalso()
    return esperar_codigo_canje(
        cliente,
        "estado1",
        cancelado,
        dormir=reloj.dormir,
        reloj=reloj.ahora,
        intervalo_s=1.0,
        limite_s=limite_s,
    )


def test_devuelve_el_codigo_en_cuanto_el_servidor_lo_tiene():
    cliente = MagicMock()
    cliente.estado_login.side_effect = [
        {"listo": False},
        {"listo": False},
        {"listo": True, "codigoCanje": "c1"},
    ]

    resultado = _esperar(cliente)

    assert resultado.estado == "exito"
    assert resultado.codigo_canje == "c1"
    assert cliente.estado_login.call_count == 3


def test_el_rechazo_del_proveedor_llega_como_error_explicado():
    cliente = MagicMock()
    cliente.estado_login.return_value = {"listo": True, "error": "sin_email"}

    resultado = _esperar(cliente)

    assert resultado.estado == "error"
    assert resultado.mensaje == mensaje_de_error("sin_email")


def test_cancelar_corta_la_espera_sin_preguntar_al_servidor():
    cliente = MagicMock()

    resultado = _esperar(cliente, cancelado=lambda: True)

    assert resultado.estado == "cancelado"
    cliente.estado_login.assert_not_called()


def test_un_404_significa_que_el_buzon_caduco():
    cliente = MagicMock()
    cliente.estado_login.side_effect = ErrorApi("estado desconocido", status_code=404)

    resultado = _esperar(cliente)

    assert resultado.estado == "error"
    assert "caducó" in resultado.mensaje


def test_un_corte_de_red_puntual_no_tira_el_intento():
    cliente = MagicMock()
    cliente.estado_login.side_effect = [
        ErrorApi("no se pudo conectar con el servidor: timeout"),
        {"listo": True, "codigoCanje": "c1"},
    ]

    resultado = _esperar(cliente)

    assert resultado.estado == "exito"


def test_si_el_servidor_no_responde_varias_veces_seguidas_se_abandona():
    cliente = MagicMock()
    cliente.estado_login.side_effect = ErrorApi("no se pudo conectar con el servidor")

    resultado = _esperar(cliente)

    assert resultado.estado == "error"
    assert "servidor" in resultado.mensaje
    # Abandona por fallos seguidos, no por agotar el tiempo.
    assert cliente.estado_login.call_count == 5


def test_agotar_el_tiempo_lo_dice_con_sus_palabras():
    cliente = MagicMock()
    cliente.estado_login.return_value = {"listo": False}

    resultado = _esperar(cliente, limite_s=3.0)

    assert resultado.estado == "error"
    assert "tiempo de espera" in resultado.mensaje


def test_el_estado_es_distinto_cada_vez_y_con_entropia_suficiente():
    uno, otro = generar_estado(), generar_estado()

    assert uno != otro
    assert len(uno) == 32  # 16 bytes en hexadecimal
