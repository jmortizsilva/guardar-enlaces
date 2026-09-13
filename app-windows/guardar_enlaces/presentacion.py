"""Formateo de texto para mostrar un elemento (logica pura, sin wx: la
columna principal del wx.ListCtrl lleva TODO el texto legible de la fila,
para que baste con que el lector de pantalla anuncie esa unica columna)."""

from __future__ import annotations

from datetime import datetime
from urllib.parse import urlparse

from .modelo import Elemento


def texto_fila(elemento: Elemento) -> str:
    partes = [elemento.titulo or elemento.url]

    dominio = urlparse(elemento.url).hostname
    if dominio:
        partes.append(dominio)

    if elemento.etiquetas:
        partes.append(", ".join(elemento.etiquetas))

    partes.append(_fecha_legible(elemento.actualizado_en))
    return " — ".join(partes)


def texto_detalle(elemento: Elemento) -> str:
    """Titulo, URL y descripcion, uno por linea, para recorrerlos con las flechas."""
    return "\n".join(parte for parte in (elemento.titulo, elemento.url, elemento.descripcion) if parte)


def _fecha_legible(timestamp_ms: int) -> str:
    if not timestamp_ms:
        return "sin fecha"
    return datetime.fromtimestamp(timestamp_ms / 1000).strftime("%d/%m/%Y")
