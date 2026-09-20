"""Mismas reglas que PruebasDuplicados.swift, en app-ios-nativa/Dominio: si
cambia el criterio de que es el mismo enlace, los dos ficheros tienen que
cambiar."""

from guardar_enlaces.duplicados import buscar_duplicado, misma_url, normalizar_url
from guardar_enlaces.modelo import Elemento


def _elemento(id_: str, url: str, borrado: bool = False) -> Elemento:
    return Elemento(
        id=id_,
        url=url,
        titulo=id_,
        descripcion=None,
        imagen_url=None,
        tipo="enlace",
        etiquetas=[],
        creado_en=0,
        actualizado_en=0,
        borrado=borrado,
    )


class TestNormalizarUrl:
    def test_http_y_https_son_la_misma_pagina(self):
        assert misma_url("http://ejemplo.com/a", "https://ejemplo.com/a")

    def test_www_barra_final_y_ancla_no_cuentan(self):
        assert misma_url("https://www.ejemplo.com/a/", "https://ejemplo.com/a#seccion")

    def test_los_parametros_de_seguimiento_se_descartan(self):
        assert misma_url(
            "https://ejemplo.com/a?utm_source=boletin&fbclid=xyz",
            "https://ejemplo.com/a",
        )

    def test_los_parametros_que_identifican_el_contenido_si_cuentan(self):
        assert not misma_url("https://ejemplo.com/ver?id=1", "https://ejemplo.com/ver?id=2")

    def test_el_orden_de_los_parametros_da_igual(self):
        assert misma_url("https://ejemplo.com/?b=2&a=1", "https://ejemplo.com/?a=1&b=2")

    def test_la_ruta_distingue_mayusculas(self):
        assert not misma_url("https://ejemplo.com/Uno", "https://ejemplo.com/uno")

    def test_lo_que_no_es_una_url_se_compara_tal_cual(self):
        assert normalizar_url("  esto no es una url  ") == "esto no es una url"
        assert not misma_url("esto no es una url", "ni esto tampoco")


class TestBuscarDuplicado:
    guardados = [
        _elemento("e1", "https://www.xataka.com/basics/alternativas-pocket"),
        _elemento("e2", "https://ejemplo.com/otro"),
    ]

    def test_encuentra_el_que_ya_estaba_aunque_venga_escrito_distinto(self):
        encontrado = buscar_duplicado(
            self.guardados,
            "http://xataka.com/basics/alternativas-pocket/?utm_source=twitter",
        )

        assert encontrado is not None
        assert encontrado.id == "e1"

    def test_una_url_nueva_no_es_duplicado_de_nada(self):
        assert buscar_duplicado(self.guardados, "https://ejemplo.com/nuevo") is None

    def test_lo_borrado_no_cuenta(self):
        con_borrado = [_elemento("e3", "https://ejemplo.com/ido", borrado=True)]

        assert buscar_duplicado(con_borrado, "https://ejemplo.com/ido") is None
