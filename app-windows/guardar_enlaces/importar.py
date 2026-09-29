"""Leer enlaces de un fichero exportado por otra aplicacion.

El comportamiento no se decide aqui: manda `IMPORTAR.md`, en la raiz del
repositorio, que vale para los tres clientes. Las pruebas de este modulo leen
los mismos ficheros de ejemplo que las de iOS y Android
(`pruebas-compartidas/importacion/`), y comparan contra el mismo
`esperado.json`. Si esto se desvia, su prueba falla.

Aqui solo se lee el fichero y se devuelve lo que trae. Guardarlo, saltarse los
repetidos y contar el resultado es de `importador.py`, que ya habla con el
almacen.
"""

from __future__ import annotations

import csv
import io
import re
import unicodedata
from collections.abc import Callable, Iterable
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from html.parser import HTMLParser

from .duplicados import normalizar_url
from .modelo import Elemento, ahora_ms, nuevo_elemento_local

# Solo direcciones de verdad. Los `javascript:` de los bookmarklets y los
# `place:` internos de Firefox acabarian en la lista como enlaces que no abren
# nada.
ESQUEMAS = ("http://", "https://")

# Las carpetas raiz de un fichero de marcadores no distinguen nada: las lleva
# todo el mundo. Una etiqueta que tienen cientos de enlaces es ruido.
CARPETAS_QUE_NO_SON_ETIQUETA = {"bookmarks", "marcadores", "bookmarks bar", "favoritos"}

HTML = "html"
CSV = "csv"
TXT = "txt"

# Cada aplicacion pone las columnas que quiere, asi que se buscan por nombre.
COLUMNAS = {
    "url": ("url", "link", "direccion", "dirección"),
    "titulo": ("title", "titulo", "título", "name", "nombre"),
    "etiquetas": ("tags", "etiquetas", "labels"),
    "carpeta": ("folder", "carpeta"),
    "fecha": ("time_added", "created", "date", "creado", "fecha"),
}


@dataclass(frozen=True)
class EnlaceImportado:
    url: str
    titulo: str | None = None
    etiquetas: tuple[str, ...] = ()
    # None es "el fichero no traia fecha": la pone quien guarde, con su reloj.
    creado_en: int | None = None


@dataclass(frozen=True)
class Lectura:
    enlaces: tuple[EnlaceImportado, ...]
    descartados: int
    formato: str


def texto_de(datos: bytes) -> str:
    """El contenido del fichero, en la codificacion que traiga.

    Quien exporta no elige la codificacion ni suele saber cual es. Los
    navegadores escriben UTF-8, pero Excel guarda los CSV en la del sistema, y
    en un Windows en espanol eso es cp1252. Se prueban por orden: primero con
    marca de orden de bytes, que es lo que pone Excel; y `latin-1` al final
    porque acepta cualquier byte, asi que hace de red y nunca lanza.
    """
    for codificacion in ("utf-8-sig", "utf-8", "cp1252", "latin-1"):
        try:
            return datos.decode(codificacion)
        except UnicodeDecodeError:
            continue
    return datos.decode("utf-8", errors="replace")


def detectar_formato(contenido: str) -> str:
    """Por el contenido y no por la extension: quien exporta no siempre la
    conserva, y un fichero de marcadores llamado .txt sigue siendo HTML."""
    principio = contenido.lstrip()[:400].lower()
    if "netscape-bookmark-file" in principio or "<dl>" in principio or "<a href" in principio:
        return HTML
    primera = _primera_linea_con_algo(contenido)
    if primera and _cabecera_de_csv(primera):
        return CSV
    return TXT


def leer(contenido: str) -> Lectura:
    formato = detectar_formato(contenido)
    if formato == HTML:
        crudos, descartados = _leer_html(contenido)
    elif formato == CSV:
        crudos, descartados = _leer_csv(contenido)
    else:
        crudos, descartados = _leer_txt(contenido)

    enlaces, repetidos = _quitar_repetidos(crudos)
    return Lectura(tuple(enlaces), descartados + repetidos, formato)


# --- HTML de marcadores ---------------------------------------------------


class _LectorDeMarcadores(HTMLParser):
    """El formato de Netscape que exportan todos los navegadores.

    Las carpetas son `<H3>` y lo que contienen va dentro del `<DL>` siguiente,
    asi que se lleva una pila: el `<H3>` deja el nombre en espera y el `<DL>`
    que viene detras lo mete.
    """

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.enlaces: list[EnlaceImportado] = []
        self.descartados = 0
        self._pila: list[str | None] = []
        self._pendiente: str | None = None
        self._pendiente_es_barra = False
        self._en_h3 = False
        self._texto: list[str] = []
        self._href: str | None = None
        self._fecha: int | None = None

    def handle_starttag(self, tag: str, atributos: list[tuple[str, str | None]]) -> None:
        valores = {clave.lower(): (valor or "") for clave, valor in atributos}
        if tag == "dl":
            # Lo que contiene una carpeta va dentro del <DL> que viene detras
            # de su <H3>: al abrirlo es cuando el nombre entra en la pila.
            self._pila.append(self._pendiente)
            self._pendiente = None
        elif tag == "h3":
            self._en_h3 = True
            self._texto = []
            # La barra de marcadores se reconoce por este atributo, y no por su
            # nombre, que cambia con el idioma del navegador.
            self._pendiente_es_barra = valores.get("personal_toolbar_folder") == "true"
        elif tag == "a":
            self._href = valores.get("href", "")
            self._fecha = _segundos_a_ms(valores.get("add_date"))
            self._texto = []

    def handle_data(self, datos: str) -> None:
        if self._en_h3 or self._href is not None:
            self._texto.append(datos)

    def handle_endtag(self, tag: str) -> None:
        if tag == "h3":
            nombre = "".join(self._texto).strip()
            self._pendiente = None if self._descartar_carpeta(nombre) else nombre
            self._en_h3 = False
            self._texto = []
        elif tag == "a":
            self._cerrar_enlace()
        elif tag == "dl":
            if self._pila:
                self._pila.pop()

    def handle_startendtag(self, tag: str, atributos: list[tuple[str, str | None]]) -> None:
        self.handle_starttag(tag, atributos)

    def _descartar_carpeta(self, nombre: str) -> bool:
        return self._pendiente_es_barra or _clave(nombre) in CARPETAS_QUE_NO_SON_ETIQUETA

    def _cerrar_enlace(self) -> None:
        href = (self._href or "").strip()
        titulo = "".join(self._texto).strip()
        self._href = None
        self._texto = []
        if not href:
            return
        if not href.lower().startswith(ESQUEMAS):
            self.descartados += 1
            return
        self.enlaces.append(
            EnlaceImportado(
                url=href,
                titulo=titulo or None,
                etiquetas=_etiquetas_unicas([self._carpeta_actual()]),
                creado_en=self._fecha,
            )
        )

    def _carpeta_actual(self) -> str | None:
        for nombre in reversed(self._pila):
            if nombre:
                return nombre
        return None


def _leer_html(contenido: str) -> tuple[list[EnlaceImportado], int]:
    lector = _LectorDeMarcadores()
    lector.feed(contenido)
    lector.close()
    return lector.enlaces, lector.descartados


# --- CSV ------------------------------------------------------------------


# Excel en espanol separa por punto y coma: la coma es el separador decimal.
SEPARADORES = (",", ";", "\t")


def _separador(cabecera: str) -> str | None:
    """Con cual de los tres separadores aparece la columna de direcciones.

    Se deduce de la cabecera y no se da por hecha la coma: al principio se
    detectaba el CSV con cualquiera de los tres, pero se leia solo con comas,
    y un CSV de Excel en espanol salia como "ese fichero no tiene ninguna
    direccion", que era mentira.
    """
    direcciones = {_clave(n) for n in COLUMNAS["url"]}
    for separador in SEPARADORES:
        if separador in cabecera and {_clave(n) for n in cabecera.split(separador)} & direcciones:
            return separador
    return None


def _cabecera_de_csv(linea: str) -> bool:
    return _separador(linea) is not None


def _leer_csv(contenido: str) -> tuple[list[EnlaceImportado], int]:
    separador = _separador(_primera_linea_con_algo(contenido) or "") or ","
    filas = list(csv.DictReader(io.StringIO(contenido), delimiter=separador))
    if not filas:
        return [], 0

    columnas = {
        campo: _columna_para(filas[0].keys(), nombres) for campo, nombres in COLUMNAS.items()
    }
    if not columnas["url"]:
        return [], 0

    enlaces: list[EnlaceImportado] = []
    descartados = 0
    for fila in filas:
        url = (fila.get(columnas["url"]) or "").strip()
        if not url.lower().startswith(ESQUEMAS):
            descartados += 1
            continue
        titulo = (fila.get(columnas["titulo"]) or "").strip() if columnas["titulo"] else ""
        carpeta = (fila.get(columnas["carpeta"]) or "").strip() if columnas["carpeta"] else ""
        etiquetas = (fila.get(columnas["etiquetas"]) or "").strip() if columnas["etiquetas"] else ""
        fecha = (fila.get(columnas["fecha"]) or "").strip() if columnas["fecha"] else ""
        enlaces.append(
            EnlaceImportado(
                url=url,
                titulo=titulo or None,
                # La carpeta primero: es la que ordena, y las etiquetas sueltas
                # vienen detras.
                etiquetas=_etiquetas_unicas([_hoja(carpeta), *_partir_etiquetas(etiquetas)]),
                creado_en=_fecha_a_ms(fecha),
            )
        )
    return enlaces, descartados


def _columna_para(cabeceras, nombres: tuple[str, ...]) -> str | None:
    buscadas = {_clave(n) for n in nombres}
    for cabecera in cabeceras:
        if cabecera and _clave(cabecera) in buscadas:
            return cabecera
    return None


def _partir_etiquetas(texto: str) -> list[str]:
    """Pocket separa por `|` y Raindrop por coma. Se aceptan las dos."""
    if not texto:
        return []
    return [t.strip() for t in re.split(r"[|,]", texto) if t.strip()]


# --- TXT ------------------------------------------------------------------


def _leer_txt(contenido: str) -> tuple[list[EnlaceImportado], int]:
    enlaces: list[EnlaceImportado] = []
    descartados = 0
    for linea in contenido.splitlines():
        if not linea.strip():
            continue
        url = _primera_direccion(linea)
        if url is None:
            descartados += 1
            continue
        enlaces.append(EnlaceImportado(url=url))
    return enlaces, descartados


def _primera_direccion(linea: str) -> str | None:
    """La primera direccion que haya en la linea, aunque venga dentro de una
    frase: mucha gente guarda «Mira esto: https://…» tal cual."""
    for trozo in linea.split():
        if trozo.lower().startswith(ESQUEMAS) and len(trozo) > 8:
            return trozo
    return None


# --- Comun ----------------------------------------------------------------


def _quitar_repetidos(enlaces: list[EnlaceImportado]) -> tuple[list[EnlaceImportado], int]:
    """Dentro del propio fichero manda el primero. Se comparan las direcciones
    como en el resto de la aplicacion, asi que dos que solo se diferencian en
    los parametros de seguimiento cuentan como la misma."""
    vistas: set[str] = set()
    unicos: list[EnlaceImportado] = []
    repetidos = 0
    for enlace in enlaces:
        clave = normalizar_url(enlace.url)
        if clave in vistas:
            repetidos += 1
            continue
        vistas.add(clave)
        unicos.append(enlace)
    return unicos, repetidos


def _etiquetas_unicas(nombres) -> tuple[str, ...]:
    """Sin repetir, y sin que cuenten mayusculas ni tildes: «Postres» y
    «postres» son la misma etiqueta, y se conserva la primera que aparecio."""
    vistas: set[str] = set()
    salida: list[str] = []
    for nombre in nombres:
        if not nombre:
            continue
        limpio = nombre.strip()
        if not limpio:
            continue
        clave = _clave(limpio)
        if clave in vistas:
            continue
        vistas.add(clave)
        salida.append(limpio)
    return tuple(salida)


def _hoja(ruta: str) -> str:
    """De «Recetas/Postres» se queda «Postres», que es la que dice algo."""
    return ruta.split("/")[-1].strip() if ruta else ""


def _clave(texto: str) -> str:
    sin_tildes = "".join(
        c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn"
    )
    return sin_tildes.strip().lower()


def _primera_linea_con_algo(contenido: str) -> str | None:
    for linea in contenido.splitlines():
        if linea.strip():
            return linea
    return None


def _segundos_a_ms(valor: str | None) -> int | None:
    """ADD_DATE viene en segundos y aqui todo va en milisegundos."""
    if not valor or not valor.strip().isdigit():
        return None
    return int(valor.strip()) * 1000


def _fecha_a_ms(valor: str) -> int | None:
    """Pocket manda segundos desde el epoch; Raindrop, una fecha ISO."""
    if not valor:
        return None
    if valor.isdigit():
        return int(valor) * 1000
    texto = valor.replace("Z", "+00:00")
    try:
        momento = datetime.fromisoformat(texto)
    except ValueError:
        return None
    if momento.tzinfo is None:
        momento = momento.replace(tzinfo=timezone.utc)
    return int(momento.timestamp() * 1000)


# --- Que se guarda de lo leido --------------------------------------------


@dataclass(frozen=True)
class Preparado:
    """Lo que hay que guardar y lo que hay que contar despues."""

    nuevos: tuple[Elemento, ...]
    ya_estaban: int
    descartados: int

    @property
    def importados(self) -> int:
        return len(self.nuevos)

    @property
    def hubo_algo(self) -> bool:
        return bool(self.nuevos) or self.ya_estaban > 0


def preparar(
    lectura: Lectura,
    existentes: Iterable[Elemento],
    ahora: Callable[[], int] = ahora_ms,
) -> Preparado:
    """Decide que enlaces de los leidos hay que guardar. Funcion pura: recibe
    lo que ya hay y devuelve lo nuevo, sin tocar la base de datos.

    Un enlace que ya tienes NO se actualiza, a diferencia de guardarlo a mano.
    Es deliberado y esta escrito en IMPORTAR.md: importar toca cientos de
    golpe y sin que los veas pasar, asi que pisar titulos y etiquetas puestos
    a mano seria un destrozo que no se puede deshacer.
    """
    # Un conjunto con las direcciones ya normalizadas, y no `buscar_duplicado`
    # por cada enlace: eso recorria la lista entera y volvia a normalizar cada
    # direccion cada vez. Medido con 800 enlaces contra 500 guardados, 2,6
    # segundos; con este, milisegundos. La comparacion es la misma.
    vistas = {normalizar_url(e.url) for e in existentes if not e.borrado}
    nuevos: list[Elemento] = []
    ya_estaban = 0

    for enlace in lectura.enlaces:
        clave = normalizar_url(enlace.url)
        if clave in vistas:
            ya_estaban += 1
            continue
        elemento = nuevo_elemento_local(
            url=enlace.url,
            titulo=enlace.titulo,
            etiquetas=enlace.etiquetas,
            ahora=ahora,
        )
        # La fecha del fichero manda cuando la trae: si no, los ochocientos
        # llegan con la de hoy y la lista deja de tener orden.
        if enlace.creado_en is not None:
            elemento = replace(elemento, creado_en=enlace.creado_en)
        nuevos.append(elemento)
        vistas.add(clave)

    return Preparado(tuple(nuevos), ya_estaban, lectura.descartados)
