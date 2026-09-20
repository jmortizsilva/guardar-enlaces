"""De donde salen el titulo y la descripcion de una URL cuando no hay cuenta.

Con cuenta los saca el servidor, que es quien los guarda para los dos
clientes; sin cuenta, la pagina la descarga esta misma aplicacion. Mismo
reparto que en el iPhone, ver ResolverMetadatos.swift.

Lo que aqui NO hace falta, y en el servidor si: comprobar que la URL no
apunta a una direccion de red privada. Alli es imprescindible porque el
servidor descarga una URL que le manda otro y podria alcanzar su red interna;
aqui la descarga el propio ordenador, con la URL que ha escrito su dueno, y
no llega a ningun sitio al que no llegase ya su navegador.

Nada de esto lanza: guardar un enlace nunca depende de que salga bien. Si no
se pudo averiguar nada se devuelve un diccionario vacio, que es lo que
dialogo_anadir ya trata como "no se pudo".
"""

from __future__ import annotations

from typing import Callable
from urllib.parse import urlparse

import requests

from .metadatos import (
    es_url_youtube,
    extraer_metadatos,
    metadatos_desde_oembed,
    url_oembed,
)

# Los mismos numeros que el servidor, para que la misma pagina se resuelva
# igual se guarde con cuenta o sin ella.
TIEMPO_LIMITE_S = 5
LIMITE_BYTES = 2_000_000
MAX_REDIRECCIONES = 5

# Quien descarga: recibe una URL y devuelve el cuerpo, o None si no se pudo.
# Se inyecta para poder probar sin red.
Descargador = Callable[[str], bytes | None]


def _sesion_http() -> requests.Session:
    sesion = requests.Session()
    sesion.max_redirects = MAX_REDIRECCIONES
    return sesion


def descargar(url: str) -> bytes | None:
    """Sin presentarse como nada en particular: algunos sitios responden un
    HTML distinto, o un muro, a lo que parece un robot, y aqui interesa justo
    lo que veria el navegador. El servidor si se identifica como bot, porque
    alli la peticion no la hace un usuario."""
    try:
        with _sesion_http() as sesion:
            respuesta = sesion.get(url, timeout=TIEMPO_LIMITE_S, stream=True)
            with respuesta:
                if not respuesta.ok:
                    return None
                trozos = bytearray()
                for trozo in respuesta.iter_content(64 * 1024):
                    trozos += trozo
                    # Se corta y se lee lo que haya: las etiquetas og: y el
                    # <title> van en la cabecera del documento, asi que un
                    # HTML gigante no aporta nada mas.
                    if len(trozos) >= LIMITE_BYTES:
                        break
                return bytes(trozos)
    except requests.RequestException:
        return None


def resolver_en_este_equipo(
    url: str, descargador: Descargador | None = None
) -> dict:
    """Titulo, descripcion, imagen y tipo, con la misma forma que responde el
    servidor. Diccionario vacio si no se pudo averiguar nada."""
    bajar = descargador or descargar

    # Mismo filtro que el servidor. Ademas de sensato, evita que requests
    # intente abrir un file:// del propio ordenador.
    if urlparse(url).scheme not in ("http", "https"):
        return {}

    # Mismo orden que el servidor: YouTube por su oEmbed, y si falla, la
    # pagina entera por el camino generico.
    if es_url_youtube(url):
        cuerpo = bajar(url_oembed(url))
        if cuerpo is not None:
            metadatos = metadatos_desde_oembed(cuerpo)
            if metadatos is not None:
                return metadatos

    cuerpo = bajar(url)
    if cuerpo is None:
        return {}
    return extraer_metadatos(cuerpo.decode("utf-8", errors="replace"))
