"""Diálogos de pregunta que hacen falta en más de un sitio.

Viven aparte porque los usan tanto el arranque (main.py) como el cambio de
cuenta desde la ventana principal, y duplicar el texto de una pregunta que
decide qué pasa con los datos de alguien es como acabas teniendo dos versiones
que dicen cosas distintas.
"""

from __future__ import annotations

import wx

from ..asentar_cuenta import EnlacesEnElEquipo


def preguntar_importacion(enlaces: EnlacesEnElEquipo, padre: wx.Window | None = None) -> bool:
    """Qué hacer con los enlaces que ya hay en el equipo al entrar en una cuenta.

    Las dos salidas van nombradas por lo que hacen, no "Sí" y "No": borrar no se
    puede deshacer y hay que oírlo antes de elegir, no después.
    """
    cuenta = "1 enlace guardado" if enlaces.cuantos == 1 else f"{enlaces.cuantos} enlaces guardados"
    origen = "con otra cuenta" if enlaces.de_otra_cuenta else "sin cuenta"
    dialogo = wx.MessageDialog(
        padre,
        f"Hay {cuenta} en este equipo {origen}. ¿Quieres añadirlos a esta "
        "cuenta? Si eliges borrarlos, se quitan de este equipo y no se pueden "
        "recuperar.",
        "Enlaces en este equipo",
        wx.YES_NO | wx.ICON_QUESTION,
    )
    dialogo.SetYesNoLabels("&Añadirlos", "&Borrarlos")
    respuesta = dialogo.ShowModal()
    dialogo.Destroy()
    return respuesta == wx.ID_YES


def avisar(texto: str, titulo: str, padre: wx.Window | None = None, grave: bool = False) -> None:
    """Un aviso con un solo botón, que dice Aceptar y no OK.

    `wx.MessageBox` no deja cambiar el texto de sus botones y sale en inglés; hay
    una prueba que impide usarlo.
    """
    dialogo = wx.MessageDialog(
        padre, texto, titulo, wx.OK | (wx.ICON_WARNING if grave else wx.ICON_INFORMATION)
    )
    dialogo.SetOKLabel("&Aceptar")
    dialogo.ShowModal()
    dialogo.Destroy()


def confirmar_eliminacion(titulo: str, padre: wx.Window | None = None) -> bool:
    """La pregunta antes de eliminar un enlace, desde la lista o desde su detalle.

    Aceptar y Cancelar y no Sí y No: un cuadro de Sí y No no tiene botón de
    cancelar, y sin él Escape no lo cierra. Cancelar es el botón de partida
    porque eliminar no se puede deshacer y un Enter de más no debería bastar.
    """
    dialogo = wx.MessageDialog(
        padre,
        f"¿Eliminar «{titulo}»? Esta acción no se puede deshacer.",
        "Confirmar eliminación",
        wx.OK | wx.CANCEL | wx.CANCEL_DEFAULT | wx.ICON_QUESTION,
    )
    dialogo.SetOKCancelLabels("&Eliminar", "&Cancelar")
    respuesta = dialogo.ShowModal()
    dialogo.Destroy()
    return respuesta == wx.ID_OK
