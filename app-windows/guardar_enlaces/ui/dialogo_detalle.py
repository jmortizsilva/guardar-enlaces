"""Detalle de un elemento guardado: abrir en el navegador, eliminar (con
confirmacion) o editar etiquetas."""

from __future__ import annotations

import webbrowser
from typing import Callable

import wx

from ..modelo import Elemento, editar
from ..presentacion import texto_detalle
from .campos import ESTILO_SOLO_LECTURA, con_etiqueta
from .preguntas import confirmar_eliminacion
from .selector_etiquetas import SelectorEtiquetas


class DialogoDetalle(wx.Dialog):
    def __init__(
        self,
        padre: wx.Window,
        elemento: Elemento,
        al_actualizar: Callable[[Elemento], None],
        al_eliminar: Callable[[Elemento], None],
        etiquetas_disponibles: list[str] | tuple[str, ...] = (),
    ):
        super().__init__(padre, title=elemento.titulo or elemento.url)
        self.elemento = elemento
        # No pueden llamarse como los metodos _al_...: el atributo tapaba al
        # metodo del boton Eliminar, que borraba sin preguntar y pasando el
        # evento de wx en lugar del elemento.
        self._avisar_actualizado = al_actualizar
        self._avisar_eliminado = al_eliminar

        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        # En StaticText, a la URL y a la descripcion no llegaba el tabulador ni
        # se podian recorrer con las flechas.
        etiqueta_enlace, self.campo_enlace = con_etiqueta(
            panel,
            "E&nlace:",
            lambda padre: wx.TextCtrl(
                padre, value=texto_detalle(elemento), style=ESTILO_SOLO_LECTURA, size=(420, 100)
            ),
        )
        sizer.Add(etiqueta_enlace, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.campo_enlace, 0, wx.EXPAND | wx.ALL, 12)

        self.etiquetas = SelectorEtiquetas(
            panel, etiquetas_disponibles, elemento.etiquetas
        )
        sizer.Add(self.etiquetas, 0, wx.EXPAND)

        botones = wx.BoxSizer(wx.HORIZONTAL)
        boton_abrir = wx.Button(panel, label="&Abrir en el navegador")
        boton_guardar_etiquetas = wx.Button(panel, label="&Guardar etiquetas")
        self.boton_eliminar = wx.Button(panel, label="&Eliminar")
        botones.Add(boton_abrir, 0, wx.RIGHT, 8)
        botones.Add(boton_guardar_etiquetas, 0, wx.RIGHT, 8)
        botones.Add(self.boton_eliminar, 0)
        sizer.Add(botones, 0, wx.ALL, 12)

        cerrar = wx.Button(panel, wx.ID_CLOSE, "&Cerrar")
        sizer.Add(cerrar, 0, wx.ALIGN_RIGHT | wx.ALL, 12)
        # Sin boton de Cancelar, Escape no sabe que boton pulsar.
        self.SetEscapeId(wx.ID_CLOSE)

        panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        boton_abrir.Bind(wx.EVT_BUTTON, self._al_abrir)
        boton_guardar_etiquetas.Bind(wx.EVT_BUTTON, self._al_guardar_etiquetas)
        self.boton_eliminar.Bind(wx.EVT_BUTTON, self._al_eliminar)
        cerrar.Bind(wx.EVT_BUTTON, lambda evento: self.EndModal(wx.ID_CLOSE))

    def _al_abrir(self, evento: wx.CommandEvent) -> None:
        webbrowser.open(self.elemento.url)

    def _al_guardar_etiquetas(self, evento: wx.CommandEvent) -> None:
        etiquetas = self.etiquetas.etiquetas_elegidas()
        self.elemento = editar(self.elemento, etiquetas=etiquetas)
        self._avisar_actualizado(self.elemento)
        self.EndModal(wx.ID_OK)

    def _al_eliminar(self, evento: wx.CommandEvent) -> None:
        if confirmar_eliminacion(self.elemento.titulo or self.elemento.url, self):
            self._avisar_eliminado(self.elemento)
            self.EndModal(wx.ID_OK)
