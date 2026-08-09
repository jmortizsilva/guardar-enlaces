"""Anadir un enlace: pegar URL -> "Comprobar" (llama a /metadatos y muestra
una vista previa) -> "Guardar" (crea el elemento local, guardado explicito,
nunca automatico)."""

from __future__ import annotations

import threading

import wx

from ..api_cliente import ClienteApi, ErrorApi
from ..modelo import Elemento, nuevo_elemento_local
from ..sesion import Sesion


class DialogoAnadir(wx.Dialog):
    def __init__(self, padre: wx.Window, cliente: ClienteApi, sesion: Sesion):
        super().__init__(padre, title="Añadir enlace")
        self._cliente = cliente
        self._sesion = sesion
        self._metadatos: dict | None = None
        self.elemento_creado: Elemento | None = None

        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        etiqueta_url = wx.StaticText(panel, label="&URL:")
        sizer.Add(etiqueta_url, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        self.campo_url = wx.TextCtrl(panel)
        self.campo_url.SetName("URL del enlace a añadir")  # ver docs/ACCESIBILIDAD-WXPYTHON.md
        self.campo_url.SetHint("https://...")
        sizer.Add(self.campo_url, 0, wx.EXPAND | wx.ALL, 12)

        self.boton_comprobar = wx.Button(panel, label="&Comprobar")
        sizer.Add(self.boton_comprobar, 0, wx.LEFT | wx.RIGHT | wx.BOTTOM, 12)

        self.etiqueta_vista_previa = wx.StaticText(panel, label="")
        sizer.Add(self.etiqueta_vista_previa, 0, wx.EXPAND | wx.ALL, 12)

        botones = wx.StdDialogButtonSizer()
        self.boton_guardar = wx.Button(panel, wx.ID_OK, "&Guardar")
        self.boton_guardar.Disable()  # hasta que haya una vista previa comprobada
        boton_cancelar = wx.Button(panel, wx.ID_CANCEL, "Cancelar")
        botones.AddButton(self.boton_guardar)
        botones.AddButton(boton_cancelar)
        botones.Realize()
        sizer.Add(botones, 0, wx.ALIGN_RIGHT | wx.ALL, 12)

        panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        self.boton_comprobar.Bind(wx.EVT_BUTTON, self._al_comprobar)
        self.boton_guardar.Bind(wx.EVT_BUTTON, self._al_guardar)
        self.campo_url.Bind(wx.EVT_TEXT, self._al_cambiar_url)
        self.campo_url.SetFocus()

    def _al_cambiar_url(self, evento: wx.CommandEvent) -> None:
        # cambiar la URL invalida la vista previa ya comprobada: hay que volver a comprobar
        self._metadatos = None
        self.boton_guardar.Disable()
        self.etiqueta_vista_previa.SetLabel("")

    def _al_comprobar(self, evento: wx.CommandEvent) -> None:
        url = self.campo_url.GetValue().strip()
        if not url.startswith(("http://", "https://")):
            self.etiqueta_vista_previa.SetLabel("Escribe una URL que empiece por http:// o https://")
            return

        self.boton_comprobar.Disable()
        self.etiqueta_vista_previa.SetLabel("Comprobando…")

        def trabajo() -> None:
            try:
                metadatos = self._sesion.con_reintento(
                    lambda token: self._cliente.metadatos(url, token)
                )
            except ErrorApi as error:
                wx.CallAfter(self._al_fallar_comprobacion, str(error))
                return
            wx.CallAfter(self._al_completar_comprobacion, url, metadatos)

        threading.Thread(target=trabajo, daemon=True).start()

    def _al_fallar_comprobacion(self, mensaje: str) -> None:
        self.boton_comprobar.Enable()
        self.etiqueta_vista_previa.SetLabel(f"No se pudo comprobar: {mensaje}")

    def _al_completar_comprobacion(self, url: str, metadatos: dict) -> None:
        self.boton_comprobar.Enable()
        self._metadatos = {**metadatos, "url": url}
        titulo = metadatos.get("titulo") or url
        self.etiqueta_vista_previa.SetLabel(titulo)
        self.etiqueta_vista_previa.GetParent().Layout()
        self.boton_guardar.Enable()
        self.boton_guardar.SetFocus()

    def _al_guardar(self, evento: wx.CommandEvent) -> None:
        if not self._metadatos:
            return
        self.elemento_creado = nuevo_elemento_local(
            url=self._metadatos["url"],
            titulo=self._metadatos.get("titulo"),
            descripcion=self._metadatos.get("descripcion"),
            imagen_url=self._metadatos.get("imagenUrl"),
            tipo=self._metadatos.get("tipo") or "enlace",
        )
        self.EndModal(wx.ID_OK)
