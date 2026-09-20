"""Detectar si una URL ya esta guardada. Logica pura, sin red ni almacen.

Comparar las URLs tal cual no sirve: la misma pagina llega con "www" o sin el,
con http o https, con una barra final de mas, con un ancla, o arrastrando los
parametros de seguimiento que anaden las redes sociales y los boletines. Todo
eso es el mismo enlace para una persona, que es quien va a oir el aviso.

Lo que NO se toca: mayusculas y minusculas de la ruta (hay servidores donde
distinguen), ni los parametros que si importan (se ordenan para comparar, pero
no se descartan).

Es duplicado solo dentro de la biblioteca de cada uno: que otra persona tenga
guardado el mismo enlace no pinta nada aqui, sus elementos ni se ven.

Calco de app-ios-nativa/Dominio/Fuentes/Dominio/Duplicados.swift. Si cambia
el criterio de que es el mismo enlace, hay que tocarlo en los dos sitios o
cada cliente avisara de cosas distintas.
"""

from __future__ import annotations

from typing import Iterable
from urllib.parse import parse_qsl, urlencode, urlsplit

from .modelo import Elemento

_PREFIJOS_DE_SEGUIMIENTO = ("utm_",)
_PARAMETROS_DE_SEGUIMIENTO = {
    "fbclid",
    "gclid",
    "igshid",
    "mc_cid",
    "mc_eid",
    "ref",
    "ref_src",
    "si",
}


def _es_de_seguimiento(clave: str) -> bool:
    minuscula = clave.lower()
    return minuscula in _PARAMETROS_DE_SEGUIMIENTO or minuscula.startswith(
        _PREFIJOS_DE_SEGUIMIENTO
    )


def normalizar_url(url: str) -> str:
    """Forma canonica para comparar, no para guardar ni para abrir: se queda sin
    esquema a proposito, porque http y https son la misma pagina para esto.

    Si la URL no se puede analizar, se devuelve tal cual en minusculas: mejor no
    detectar un duplicado que inventarse uno.
    """
    limpia = url.strip()
    try:
        partes = urlsplit(limpia)
    except ValueError:
        return limpia.lower()
    if not partes.hostname:
        return limpia.lower()

    host = partes.hostname.lower()
    if host.startswith("www."):
        host = host[4:]
    ruta = partes.path.rstrip("/")
    parametros = sorted(
        (c, v) for c, v in parse_qsl(partes.query) if not _es_de_seguimiento(c)
    )
    consulta = f"?{urlencode(parametros)}" if parametros else ""
    return f"{host}{ruta}{consulta}"


def misma_url(una: str, otra: str) -> bool:
    return normalizar_url(una) == normalizar_url(otra)


def buscar_duplicado(elementos: Iterable[Elemento], url: str) -> Elemento | None:
    """El elemento ya guardado con esa misma URL, si lo hay. Los borrados no
    cuentan: si lo tiraste, volver a guardarlo es un alta normal."""
    buscada = normalizar_url(url)
    for elemento in elementos:
        if not elemento.borrado and normalizar_url(elemento.url) == buscada:
            return elemento
    return None
