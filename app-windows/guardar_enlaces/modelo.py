"""Logica pura del dominio: el elemento guardado y la fusion de sincronizacion.

Sin red ni interfaz aqui (regla del proyecto: separar la logica que se puede
probar sin dispositivo/servidor de la fontaneria). El reloj se inyecta
(``ahora_ms``) en vez de leerse directamente, para que los tests sean
deterministas.

Los timestamps son enteros en MILISEGUNDOS (como ``Date.now()`` en el
backend), no segundos: hay que multiplicar por 1000 al leer ``time.time()``.
"""

from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field, replace
from typing import Callable, Optional


def ahora_ms() -> int:
    return int(time.time() * 1000)


@dataclass(frozen=True)
class Elemento:
    id: str
    url: str
    titulo: Optional[str] = None
    descripcion: Optional[str] = None
    imagen_url: Optional[str] = None
    tipo: str = "enlace"
    etiquetas: tuple[str, ...] = field(default_factory=tuple)
    creado_en: int = 0
    actualizado_en: int = 0
    borrado: bool = False

    def to_json_dict(self) -> dict:
        """Al formato que espera el API (camelCase, etiquetas como lista)."""
        return {
            "id": self.id,
            "url": self.url,
            "titulo": self.titulo,
            "descripcion": self.descripcion,
            "imagenUrl": self.imagen_url,
            "tipo": self.tipo,
            "etiquetas": list(self.etiquetas),
            "creadoEn": self.creado_en,
            "actualizadoEn": self.actualizado_en,
            "borrado": self.borrado,
        }

    @staticmethod
    def from_json_dict(datos: dict) -> "Elemento":
        """Desde la respuesta del API (camelCase) o de un pull/push."""
        return Elemento(
            id=datos["id"],
            url=datos.get("url") or "",
            titulo=datos.get("titulo"),
            descripcion=datos.get("descripcion"),
            imagen_url=datos.get("imagenUrl"),
            tipo=datos.get("tipo") or "enlace",
            etiquetas=tuple(datos.get("etiquetas") or ()),
            creado_en=datos.get("creadoEn", 0),
            actualizado_en=datos.get("actualizadoEn", 0),
            borrado=bool(datos.get("borrado", False)),
        )


def nuevo_elemento_local(
    url: str,
    titulo: Optional[str] = None,
    descripcion: Optional[str] = None,
    imagen_url: Optional[str] = None,
    tipo: str = "enlace",
    etiquetas: tuple[str, ...] = (),
    ahora: Callable[[], int] = ahora_ms,
) -> Elemento:
    """Crea un elemento nuevo con un id generado localmente (uuid4): permite
    guardarlo sin conexion y hace el push idempotente (ver CONTRATO-API.md).
    """
    ts = ahora()
    return Elemento(
        id=str(uuid.uuid4()),
        url=url,
        titulo=titulo,
        descripcion=descripcion,
        imagen_url=imagen_url,
        tipo=tipo,
        etiquetas=tuple(etiquetas),
        creado_en=ts,
        actualizado_en=ts,
        borrado=False,
    )


def marcar_borrado(elemento: Elemento, ahora: Callable[[], int] = ahora_ms) -> Elemento:
    """Baja logica (tombstone), nunca se borra la fila de golpe: el servidor
    necesita ver el 'borrado' para avisar a los demas dispositivos."""
    return replace(elemento, borrado=True, actualizado_en=ahora())


def editar(
    elemento: Elemento,
    ahora: Callable[[], int] = ahora_ms,
    **cambios,
) -> Elemento:
    """Aplica cambios de campos (titulo, etiquetas, ...) y actualiza el
    timestamp. No se puede reeditar un elemento ya borrado con esto."""
    return replace(elemento, actualizado_en=ahora(), **cambios)


Cache = dict[str, Elemento]


def aplicar_pull(cache: Cache, recibidos: list[Elemento], pendientes: Cache) -> Cache:
    """Fusiona el resultado de un GET /sincronizar en la cache local.

    El servidor ya resolvio los conflictos entre dispositivos, asi que en
    principio basta con sobrescribir. La unica salvedad es no pisar un
    cambio local que todavia esta en el outbox (pendiente de subir) y es MAS
    reciente que lo que acaba de llegar del pull: si no, un pull que se
    solape con un push en curso podria hacer "retroceder" visualmente un
    cambio que el usuario acaba de hacer, hasta que el push confirme.
    """
    nueva = dict(cache)
    for elemento in recibidos:
        pendiente = pendientes.get(elemento.id)
        if pendiente is not None and pendiente.actualizado_en > elemento.actualizado_en:
            continue
        nueva[elemento.id] = elemento
    return nueva


def aplicar_respuesta_push(cache: Cache, definitivos: list[Elemento]) -> Cache:
    """El POST /sincronizar devuelve la version DEFINITIVA de cada elemento
    del lote (puede diferir de lo enviado si se perdio un conflicto): la
    cache local se sustituye siempre por lo que responde el servidor."""
    nueva = dict(cache)
    for elemento in definitivos:
        nueva[elemento.id] = elemento
    return nueva


def elementos_visibles(cache: Cache) -> list[Elemento]:
    """Los que no estan borrados, mas recientes primero."""
    vivos = [e for e in cache.values() if not e.borrado]
    return sorted(vivos, key=lambda e: e.actualizado_en, reverse=True)


def buscar(elementos: list[Elemento], consulta: str) -> list[Elemento]:
    """Filtro local sobre titulo, url y etiquetas (sin distinguir mayusculas)."""
    q = consulta.strip().lower()
    if not q:
        return elementos
    return [
        e
        for e in elementos
        if q in (e.titulo or "").lower()
        or q in e.url.lower()
        or any(q in etiqueta.lower() for etiqueta in e.etiquetas)
    ]


def etiquetas_disponibles(elementos: list[Elemento]) -> list[str]:
    """Etiquetas distintas presentes, ordenadas: para rellenar el selector
    de filtro del buscador."""
    return sorted({etiqueta for e in elementos for etiqueta in e.etiquetas})


def recuento_por_etiqueta(elementos: list[Elemento]) -> dict[str, int]:
    """Cuantos enlaces lleva cada etiqueta: para mostrarlo en el gestor de
    etiquetas."""
    recuento: dict[str, int] = {}
    for e in elementos:
        for etiqueta in e.etiquetas:
            recuento[etiqueta] = recuento.get(etiqueta, 0) + 1
    return recuento


def renombrar_etiqueta(
    elementos: list[Elemento], vieja: str, nueva: str, ahora: Callable[[], int] = ahora_ms
) -> list[Elemento]:
    """Los elementos que llevaban `vieja`, con esa etiqueta sustituida por
    `nueva`. Si el elemento ya tenia `nueva` tambien, no se duplica (dos
    etiquetas fusionandose en una es un resultado valido, no un error)."""
    cambiados = []
    for e in elementos:
        if vieja not in e.etiquetas:
            continue
        etiquetas = tuple(dict.fromkeys(nueva if et == vieja else et for et in e.etiquetas))
        cambiados.append(editar(e, ahora=ahora, etiquetas=etiquetas))
    return cambiados


def quitar_etiqueta(
    elementos: list[Elemento], etiqueta: str, ahora: Callable[[], int] = ahora_ms
) -> list[Elemento]:
    """Los elementos que llevaban `etiqueta`, sin ella."""
    cambiados = []
    for e in elementos:
        if etiqueta not in e.etiquetas:
            continue
        etiquetas = tuple(et for et in e.etiquetas if et != etiqueta)
        cambiados.append(editar(e, ahora=ahora, etiquetas=etiquetas))
    return cambiados


def filtrar_por_etiqueta(elementos: list[Elemento], etiqueta: Optional[str]) -> list[Elemento]:
    """Sin etiqueta (None o cadena vacia) no filtra nada."""
    if not etiqueta:
        return elementos
    return [e for e in elementos if etiqueta in e.etiquetas]


@dataclass(frozen=True)
class EtiquetaDefinida:
    """Una etiqueta que existe por si sola, sin que ningun elemento la lleve
    todavia -- para poder crearla en un sitio y verla en el otro antes de
    usarla. Mismo mecanismo de sincronizacion que Elemento (id de cliente,
    tombstone, gana el timestamp mas reciente), sin url ni el resto de
    campos que no le hacen falta."""

    id: str
    nombre: str
    creado_en: int = 0
    actualizado_en: int = 0
    borrado: bool = False

    def to_json_dict(self) -> dict:
        return {
            "id": self.id,
            "nombre": self.nombre,
            "creadoEn": self.creado_en,
            "actualizadoEn": self.actualizado_en,
            "borrado": self.borrado,
        }

    @staticmethod
    def from_json_dict(datos: dict) -> "EtiquetaDefinida":
        return EtiquetaDefinida(
            id=datos["id"],
            nombre=datos.get("nombre") or "",
            creado_en=datos.get("creadoEn", 0),
            actualizado_en=datos.get("actualizadoEn", 0),
            borrado=bool(datos.get("borrado", False)),
        )


def nueva_etiqueta_definida(nombre: str, ahora: Callable[[], int] = ahora_ms) -> EtiquetaDefinida:
    ts = ahora()
    return EtiquetaDefinida(id=str(uuid.uuid4()), nombre=nombre, creado_en=ts, actualizado_en=ts)


def renombrar_etiqueta_definida(
    etiqueta: EtiquetaDefinida, nombre: str, ahora: Callable[[], int] = ahora_ms
) -> EtiquetaDefinida:
    return replace(etiqueta, nombre=nombre, actualizado_en=ahora())


def eliminar_etiqueta_definida(
    etiqueta: EtiquetaDefinida, ahora: Callable[[], int] = ahora_ms
) -> EtiquetaDefinida:
    return replace(etiqueta, borrado=True, actualizado_en=ahora())


CacheEtiquetas = dict[str, EtiquetaDefinida]


def aplicar_pull_etiquetas(
    cache: CacheEtiquetas, recibidas: list[EtiquetaDefinida], pendientes: CacheEtiquetas
) -> CacheEtiquetas:
    """Mismo criterio que aplicar_pull: no pisar un cambio local pendiente
    mas reciente que lo que acaba de llegar."""
    nueva = dict(cache)
    for etiqueta in recibidas:
        pendiente = pendientes.get(etiqueta.id)
        if pendiente is not None and pendiente.actualizado_en > etiqueta.actualizado_en:
            continue
        nueva[etiqueta.id] = etiqueta
    return nueva


def aplicar_respuesta_push_etiquetas(
    cache: CacheEtiquetas, definitivas: list[EtiquetaDefinida]
) -> CacheEtiquetas:
    nueva = dict(cache)
    for etiqueta in definitivas:
        nueva[etiqueta.id] = etiqueta
    return nueva


def etiquetas_reservadas_visibles(cache: CacheEtiquetas) -> list[EtiquetaDefinida]:
    """Las que no estan borradas."""
    return [e for e in cache.values() if not e.borrado]
