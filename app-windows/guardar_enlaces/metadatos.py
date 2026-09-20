"""Titulo, descripcion, imagen y tipo de una URL, leidos de un HTML ya
descargado. Logica pura: sin red y sin libreria de analisis de HTML, un par
de expresiones regulares tolerantes.

Calco de backend/src/metadatos/extraccion.ts (el canonico) y de
app-ios-nativa/Dominio/Fuentes/Dominio/Metadatos.swift. Los tres proyectos
del repositorio no comparten codigo a proposito, asi que esto esta duplicado
TRES veces: si cambia el criterio de que es un video o un articulo, hay que
tocarlo en los tres sitios, o el mismo enlace saldra distinto segun quien
resolviera sus metadatos.

Devuelve el mismo diccionario que responde POST /metadatos del servidor,
"imagenUrl" en camelCase incluido, que es la forma del contrato. Asi quien
llama no tiene que saber cual de los dos lo resolvio.
"""

from __future__ import annotations

import json
import re
from urllib.parse import quote, urlparse

# Solo estas cinco, las mismas que el backend. "&eacute;" y companeras se
# quedan sin traducir y eso se nota en el titulo. No se arregla aqui a solas:
# hacerlo solo en Windows es justo lo que haria que el mismo enlace se viera
# distinto segun quien resolvio sus metadatos.
_ENTIDADES = (
    ("&amp;", "&"),
    ("&lt;", "<"),
    ("&gt;", ">"),
    ("&quot;", '"'),
    ("&#39;", "'"),
)


def _decodificar_entidades(texto: str) -> str:
    for entidad, caracter in _ENTIDADES:
        texto = texto.replace(entidad, caracter)
    return texto


def _meta_open_graph(html: str, propiedad: str) -> str | None:
    """El orden de property y content cambia de un sitio a otro, asi que se
    prueban las dos formas."""
    patrones = (
        rf"<meta[^>]+property=[\"']og:{propiedad}[\"'][^>]+content=[\"']([^\"']*)[\"']",
        rf"<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:{propiedad}[\"']",
    )
    for patron in patrones:
        coincidencia = re.search(patron, html, re.IGNORECASE)
        if coincidencia:
            return _decodificar_entidades(coincidencia.group(1))
    return None


def _titulo_de_la_pestana(html: str) -> str | None:
    coincidencia = re.search(r"<title[^>]*>([^<]*)</title>", html, re.IGNORECASE)
    if not coincidencia:
        return None
    # Un <title> vacio o solo con espacios vale lo mismo que no tenerlo.
    return _decodificar_entidades(coincidencia.group(1).strip()) or None


def _tipo_segun_open_graph(og_type: str | None) -> str:
    if not og_type:
        return "enlace"
    if "video" in og_type:
        return "video"
    if "article" in og_type:
        return "articulo"
    if "image" in og_type or "photo" in og_type:
        return "imagen"
    return "enlace"


def extraer_metadatos(html: str) -> dict:
    """Lo que se puede sacar de la pagina. Nunca falla: si no hay nada que
    leer, devuelve el diccionario con todo a None y tipo "enlace"."""
    return {
        "titulo": _meta_open_graph(html, "title") or _titulo_de_la_pestana(html),
        "descripcion": _meta_open_graph(html, "description"),
        "imagenUrl": _meta_open_graph(html, "image"),
        "tipo": _tipo_segun_open_graph(_meta_open_graph(html, "type")),
    }


# --- YouTube aparte ---------------------------------------------------------
#
# Su oEmbed publico da titulo y miniatura mas fiables que raspar og:*, y no
# necesita credenciales. Calco de backend/src/metadatos/youtube.ts.

_DOMINIOS_YOUTUBE = frozenset(
    {"youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"}
)


def es_url_youtube(url: str) -> bool:
    try:
        return (urlparse(url).hostname or "").lower() in _DOMINIOS_YOUTUBE
    except ValueError:
        return False


def url_oembed(url: str) -> str:
    """La URL del video acaba dentro de un parametro, asi que se escapa
    entera: dos puntos y barras incluidos. `safe=""` deja sin escapar lo
    mismo que el iPhone (letras, digitos y `-._~`)."""
    return f"https://www.youtube.com/oembed?url={quote(url, safe='')}&format=json"


def metadatos_desde_oembed(cuerpo: bytes | str) -> dict | None:
    """Lee la respuesta del oEmbed. Si no se entiende devuelve None, y quien
    llama cae al raspado generico, que es lo que hace el servidor."""
    try:
        datos = json.loads(cuerpo)
    except (ValueError, TypeError):
        return None
    if not isinstance(datos, dict):
        return None
    return {
        "titulo": datos.get("title"),
        "descripcion": None,
        "imagenUrl": datos.get("thumbnail_url"),
        "tipo": "video",
    }
