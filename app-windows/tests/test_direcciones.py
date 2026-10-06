"""Completar la direccion escrita a mano, contra los casos compartidos.

Como las de importar, estas pruebas no traen sus casos: leen
pruebas-compartidas/direcciones/casos.json, el mismo que leen iOS y Android.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from guardar_enlaces.api_cliente import ErrorApi
from guardar_enlaces.direcciones import (
    CARGA,
    NO_CARGA,
    SIN_COMPROBAR,
    Comprobacion,
    DireccionComprobada,
    DireccionEscrita,
    completar,
    comprobar_direccion,
)
from guardar_enlaces.resolver_metadatos import comprobacion_del_servidor, comprobar_en_este_equipo

CASOS = Path(__file__).resolve().parents[2] / "pruebas-compartidas" / "direcciones" / "casos.json"


def _casos() -> list[dict]:
    return json.loads(CASOS.read_text(encoding="utf-8"))["casos"]


@pytest.mark.parametrize("caso", _casos(), ids=lambda caso: repr(caso["escrito"]))
def test_completa_como_dicen_los_casos_compartidos(caso):
    resultado = completar(caso["escrito"])
    if caso["direccion"] is None:
        assert resultado is None
    else:
        assert resultado is not None
        assert resultado.direccion == caso["direccion"]
        assert resultado.alternativa == caso["alternativa"]


# --- Que direccion se guarda segun cargue o no ----------------------------

COMPLETADA = DireccionEscrita("https://viejo.es", "http://viejo.es")
ESCRITA_ENTERA = DireccionEscrita("https://viejo.es", None)
CARGADA = Comprobacion(CARGA, {"titulo": "Viejo"})


def _respuestas(por_direccion: dict):
    preguntadas = []

    def comprobar(url: str) -> Comprobacion:
        preguntadas.append(url)
        return por_direccion[url]

    return comprobar, preguntadas


def test_si_carga_con_https_no_se_prueba_nada_mas():
    comprobar, preguntadas = _respuestas({"https://viejo.es": CARGADA})
    assert comprobar_direccion(COMPLETADA, comprobar) == DireccionComprobada(
        "https://viejo.es", CARGADA
    )
    assert preguntadas == ["https://viejo.es"]


def test_si_no_carga_con_https_y_lo_puso_la_app_se_queda_con_http_si_carga():
    comprobar, _ = _respuestas(
        {"https://viejo.es": Comprobacion(NO_CARGA), "http://viejo.es": CARGADA}
    )
    assert comprobar_direccion(COMPLETADA, comprobar) == DireccionComprobada(
        "http://viejo.es", CARGADA
    )


def test_si_no_carga_ninguna_se_queda_con_la_de_https():
    comprobar, _ = _respuestas(
        {"https://viejo.es": Comprobacion(NO_CARGA), "http://viejo.es": Comprobacion(NO_CARGA)}
    )
    assert comprobar_direccion(COMPLETADA, comprobar) == DireccionComprobada(
        "https://viejo.es", Comprobacion(NO_CARGA)
    )


def test_si_el_esquema_lo_escribio_quien_usa_la_app_no_se_prueba_otro():
    comprobar, preguntadas = _respuestas({"https://viejo.es": Comprobacion(NO_CARGA)})
    assert comprobar_direccion(ESCRITA_ENTERA, comprobar).comprobacion.estado == NO_CARGA
    assert preguntadas == ["https://viejo.es"]


def test_sin_poder_comprobar_no_se_prueba_la_alternativa():
    comprobar, preguntadas = _respuestas({"https://viejo.es": Comprobacion(SIN_COMPROBAR)})
    assert comprobar_direccion(COMPLETADA, comprobar).comprobacion.estado == SIN_COMPROBAR
    assert preguntadas == ["https://viejo.es"]


# --- Si carga, en este equipo y en el servidor ----------------------------


def test_en_este_equipo_una_pagina_que_responde_carga_aunque_no_tenga_titulo():
    resultado = comprobar_en_este_equipo(
        "https://a.com", hay_red=lambda: True, descargador=lambda url: b"<html>hola</html>"
    )
    assert resultado.estado == CARGA


def test_en_este_equipo_si_falla_con_red_no_carga():
    resultado = comprobar_en_este_equipo(
        "https://noexiste.es", hay_red=lambda: True, descargador=lambda url: None
    )
    assert resultado.estado == NO_CARGA


def test_en_este_equipo_si_falla_sin_red_no_se_sabe():
    resultado = comprobar_en_este_equipo(
        "https://a.com", hay_red=lambda: False, descargador=lambda url: None
    )
    assert resultado.estado == SIN_COMPROBAR


def test_en_el_servidor_400_y_502_es_que_no_carga():
    for codigo in (400, 502):

        def pedir(codigo=codigo):
            raise ErrorApi("no", codigo)

        assert comprobacion_del_servidor(pedir).estado == NO_CARGA


def test_en_el_servidor_sin_respuesta_o_un_fallo_suyo_no_se_sabe():
    for codigo in (None, 500):

        def pedir(codigo=codigo):
            raise ErrorApi("no", codigo)

        assert comprobacion_del_servidor(pedir).estado == SIN_COMPROBAR


def test_en_el_servidor_si_contesta_carga_con_sus_metadatos():
    resultado = comprobacion_del_servidor(lambda: {"titulo": "A"})
    assert resultado == Comprobacion(CARGA, {"titulo": "A"})
