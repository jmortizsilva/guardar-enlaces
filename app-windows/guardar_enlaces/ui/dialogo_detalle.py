"""Detalle de un elemento guardado: abrir en el navegador, eliminar (con
confirmacion) o editar etiquetas."""

from __future__ import annotations

import webbrowser
from typing import Callable

import wx

from ..modelo import Elemento, editar, marcar_borrado


class DialogoDetalle(wx.Dialog):
    def __init__(
        self,
        padre: wx.Window,
        elemento: Elemento,
        al_actualizar: Callable[[Elemento], None],
        al_eliminar: Callable[[Elemento], None],
    ):
        super().__init__(padre, title=elemento.titulo or elemento.url)
        self.elemento = elemento
        self._al_actualizar = al_actualizar
        self._al_eliminar = al_eliminar

        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        titulo = wx.StaticText(panel, label=elemento.titulo or elemento.url)
        fuente_titulo = titulo.GetFont()
        fuente_titulo.SetWeight(wx.FONTWEIGHT_BOLD)
        titulo.SetFont(fuente_titulo)
        sizer.Add(titulo, 0, wx.ALL, 12)

        sizer.Add(wx.StaticText(panel, label=elemento.url), 0, wx.LEFT | wx.RIGHT | wx.BOTTOM, 12)

        if elemento.descripcion:
            descripcion = wx.StaticText(panel, label=elemento.descripcion)
            descripcion.Wrap(400)
            sizer.Add(descripcion, 0, wx.LEFT | wx.RIGHT | wx.BOTTOM, 12)

        sizer.Add(
            wx.StaticText(panel, label="Etiquetas:"), 0, wx.LEFT | wx.RIGHT | wx.TOP, 12
        )
        self.campo_etiquetas = wx.TextCtrl(panel, value=", ".join(elemento.etiquetas))
        self.campo_etiquetas.SetHint("separadas por comas")
        sizer.Add(self.campo_etiquetas, 0, wx.EXPAND | wx.ALL, 12)

        botones = wx.BoxSizer(wx.HORIZONTAL)
        boton_abrir = wx.Button(panel, label="&Abrir en el navegador")
        boton_guardar_etiquetas = wx.Button(panel, label="&Guardar etiquetas")
        boton_eliminar = wx.Button(panel, label="&Eliminar")
        botones.Add(boton_abrir, 0, wx.RIGHT, 8)
        botones.Add(boton_guardar_etiquetas, 0, wx.RIGHT, 8)
        botones.Add(boton_eliminar, 0)
        sizer.Add(botones, 0, wx.ALL, 12)

        cerrar = wx.Button(panel, wx.ID_CLOSE, "Cerrar")
        sizer.Add(cerrar, 0, wx.ALIGN_RIGHT | wx.ALL, 12)

        panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        boton_abrir.Bind(wx.EVT_BUTTON, self._al_abrir)
        boton_guardar_etiquetas.Bind(wx.EVT_BUTTON, self._al_guardar_etiquetas)
        boton_eliminar.Bind(wx.EVT_BUTTON, self._al_eliminar)
        cerrar.Bind(wx.EVT_BUTTON, lambda evento: self.EndModal(wx.ID_CLOSE))

    def _al_abrir(self, evento: wx.CommandEvent) -> None:
        webbrowser.open(self.elemento.url)

    def _al_guardar_etiquetas(self, evento: wx.CommandEvent) -> None:
        etiquetas = tuple(
            e.strip() for e in self.campo_etiquetas.GetValue().split(",") if e.strip()
        )
        self.elemento = editar(self.elemento, etiquetas=etiquetas)
        self._al_actualizar(self.elemento)
        self.EndModal(wx.ID_OK)

    def _al_eliminar(self, evento: wx.CommandEvent) -> None:
        titulo = self.elemento.titulo or self.elemento.url
        respuesta = wx.MessageBox(
            f"¿Eliminar «{titulo}»? Esta acción no se puede deshacer.",
            "Confirmar eliminación",
            wx.YES_NO | wx.ICON_QUESTION,
            self,
        )
        if respuesta == wx.YES:
            self._al_eliminar_confirmado()

    def _al_eliminar_confirmado(self) -> None:
        self._al_eliminar(self.elemento)
        self.EndModal(wx.ID_OK)
