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


# --- Importar de otra aplicacion ------------------------------------------


def texto_resultado_importacion(importados: int, ya_estaban: int) -> str:
    """Lo que se dice al terminar de importar.

    Se cuenta el resultado, no que la accion ocurrio: «Importados 143 enlaces»
    y no «Importacion completada», que no dice nada de lo que ha pasado.

    Los descartados (bookmarklets, lineas sin direccion, repetidos dentro del
    propio fichero) NO se mencionan cuando entro algo: son ruido del fichero de
    origen y no hay nada que hacer con ellos. Si no entro nada, entonces si hay
    que explicar por que.
    """
    if importados == 0 and ya_estaban == 0:
        return "Ese fichero no tiene ninguna dirección."
    if importados == 0 and ya_estaban == 1:
        return "No hay nada nuevo: el único enlace del fichero ya lo tenías."
    if importados == 0:
        return f"No hay nada nuevo: los {ya_estaban} enlaces del fichero ya los tenías."

    frase = "Importado 1 enlace." if importados == 1 else f"Importados {importados} enlaces."
    if ya_estaban == 0:
        return frase
    if ya_estaban == 1:
        return f"{frase} Uno ya lo tenías."
    return f"{frase} {ya_estaban} ya los tenías."


# --- Gestionar etiquetas ----------------------------------------------------


def pregunta_eliminar_etiqueta(etiqueta: str, cuantos: int) -> str:
    """Se avisa antes porque toca todos los enlaces que la llevan. Una
    etiqueta creada de antemano puede no llevarla ninguno, y «Se quitará de 0
    enlaces» no se le dice a nadie."""
    if cuantos == 0:
        return f"¿Eliminar la etiqueta «{etiqueta}»? No la lleva ningún enlace."
    return (
        f"¿Eliminar la etiqueta «{etiqueta}»? Se quitará de {_enlaces(cuantos)}. "
        "Esta acción no se puede deshacer."
    )


def texto_etiqueta_renombrada(vieja: str, nueva: str, cuantos: int) -> str:
    if cuantos == 0:
        return f"Etiqueta «{vieja}» renombrada a «{nueva}»"
    return f"Etiqueta «{vieja}» renombrada a «{nueva}» en {_enlaces(cuantos)}"


def texto_etiqueta_eliminada(etiqueta: str, cuantos: int) -> str:
    if cuantos == 0:
        return f"Etiqueta «{etiqueta}» eliminada"
    return f"Etiqueta «{etiqueta}» eliminada de {_enlaces(cuantos)}"


def _enlaces(cuantos: int) -> str:
    """Plurales concordados de verdad, nunca «enlace(s)»."""
    return "1 enlace" if cuantos == 1 else f"{cuantos} enlaces"
