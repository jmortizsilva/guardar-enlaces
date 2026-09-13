"""Controles con su etiqueta, creada como la necesita el lector de pantalla.

En Windows el nombre accesible de un campo sale del `wx.StaticText` creado
**justo antes** que él y con el mismo padre. Cuenta el orden de creación, no el
del sizer ni la posición en pantalla. `SetName()` no sirve: es el nombre interno
del control y no llega al lector. Ver docs/ACCESIBILIDAD-WXPYTHON.md.
"""

from __future__ import annotations

from typing import Callable, TypeVar

import wx

Control = TypeVar("Control", bound=wx.Window)

# De una sola línea, un campo de sólo lectura no recibe el tabulador.
ESTILO_SOLO_LECTURA = wx.TE_READONLY | wx.TE_MULTILINE


def con_etiqueta(
    padre: wx.Window, etiqueta: str, crear: Callable[[wx.Window], Control]
) -> tuple[wx.StaticText, Control]:
    """Crea la etiqueta y, a continuación, el control.

    Recibe una función y no el control ya hecho porque, si el control existiera
    antes, la etiqueta quedaría detrás y no le daría nombre: se ve igual en
    pantalla y el lector no dice nada. Dónde se colocan los dos en el sizer lo
    decide quien llama; eso no cambia el nombre.
    """
    texto = wx.StaticText(padre, label=etiqueta)
    return texto, crear(padre)


def etiqueta_widget_de(control: wx.Window) -> wx.StaticText | None:
    """El `wx.StaticText` que da nombre a este control, si lo hay.

    Es el hermano inmediatamente anterior; `GetChildren()` respeta el orden de
    creación."""
    padre = control.GetParent()
    if padre is None:
        return None
    anterior = None
    for hermano in padre.GetChildren():
        if hermano is control:
            return anterior if isinstance(anterior, wx.StaticText) else None
        anterior = hermano
    return None


def etiqueta_de(control: wx.Window) -> str:
    """Lo que el lector anunciaría como nombre del control, sin la marca `&`."""
    texto = etiqueta_widget_de(control)
    return texto.GetLabelText() if texto else ""


def mostrar_con_etiqueta(control: wx.Window, visible: bool) -> None:
    """Enseña u oculta un campo junto con su etiqueta.

    Un campo vacío por el que pasa el tabulador es una parada que no dice nada;
    mejor que no esté hasta que tenga algo que leer. La etiqueta va con él para
    no dejar suelto el nombre de un campo que no se ve."""
    control.Show(visible)
    texto = etiqueta_widget_de(control)
    if texto is not None:
        texto.Show(visible)
