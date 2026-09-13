"""Anadir un enlace: pegar URL -> "Comprobar" (llama a /metadatos y muestra
una vista previa) -> "Guardar" (crea el elemento local, guardado explicito,
nunca automatico).

Si la URL ya esta guardada se avisa, pero no se prohibe: puede que quieras
guardarla otra vez con otras etiquetas. El aviso va en el texto de la vista
previa Y en la etiqueta del boton, porque el boton es lo que recibe el foco al
terminar la comprobacion y es lo unico que el lector de pantalla lee solo."""

from __future__ import annotations

import threading

import wx

from ..api_cliente import ClienteApi, ErrorApi
from ..duplicados import buscar_duplicado
from ..modelo import Elemento, nuevo_elemento_local
from ..sesion import Sesion
from .campos import ESTILO_SOLO_LECTURA, con_etiqueta, mostrar_con_etiqueta


class DialogoAnadir(wx.Dialog):
    def __init__(
        self,
        padre: wx.Window,
        cliente: ClienteApi,
        sesion: Sesion,
        guardados: list[Elemento] | None = None,
    ):
        super().__init__(padre, title="Añadir enlace")
        self._cliente = cliente
        self._sesion = sesion
        self._guardados = guardados or []
        self._metadatos: dict | None = None
        self.elemento_creado: Elemento | None = None

        self._panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        etiqueta_url, self.campo_url = con_etiqueta(self._panel, "&URL:", wx.TextCtrl)
        self.campo_url.SetHint("https://...")
        sizer.Add(etiqueta_url, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.campo_url, 0, wx.EXPAND | wx.ALL, 12)

        self.boton_comprobar = wx.Button(self._panel, label="Com&probar")
        sizer.Add(self.boton_comprobar, 0, wx.LEFT | wx.RIGHT | wx.BOTTOM, 12)

        # Cuadro de solo lectura y no StaticText, para poder leerla con las
        # flechas. Oculto mientras no haya nada que mostrar.
        etiqueta_vista_previa, self.vista_previa = con_etiqueta(
            self._panel,
            "&Vista previa:",
            lambda padre: wx.TextCtrl(padre, style=ESTILO_SOLO_LECTURA, size=(420, 80)),
        )
        sizer.Add(etiqueta_vista_previa, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.vista_previa, 0, wx.EXPAND | wx.ALL, 12)
        mostrar_con_etiqueta(self.vista_previa, False)

        botones = wx.StdDialogButtonSizer()
        self.boton_guardar = wx.Button(self._panel, wx.ID_OK, "&Guardar")
        self.boton_guardar.Disable()  # hasta que haya una vista previa comprobada
        boton_cancelar = wx.Button(self._panel, wx.ID_CANCEL, "&Cancelar")
        botones.AddButton(self.boton_guardar)
        botones.AddButton(boton_cancelar)
        botones.Realize()
        sizer.Add(botones, 0, wx.ALIGN_RIGHT | wx.ALL, 12)

        self._panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(self._panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        self.boton_comprobar.Bind(wx.EVT_BUTTON, self._al_comprobar)
        self.boton_guardar.Bind(wx.EVT_BUTTON, self._al_guardar)
        self.campo_url.Bind(wx.EVT_TEXT, self._al_cambiar_url)
        self.campo_url.SetFocus()

    def _mostrar_vista_previa(self, texto: str) -> None:
        if not texto and not self.vista_previa.IsShown():
            return  # cada tecla en la URL pasa por aqui
        self.vista_previa.SetValue(texto)
        mostrar_con_etiqueta(self.vista_previa, bool(texto))
        self._panel.Layout()
        self.Fit()

    def _al_cambiar_url(self, evento: wx.CommandEvent) -> None:
        # cambiar la URL invalida la vista previa ya comprobada: hay que volver a comprobar
        self._metadatos = None
        self.boton_guardar.Disable()
        self._mostrar_vista_previa("")

    def _al_comprobar(self, evento: wx.CommandEvent) -> None:
        url = self.campo_url.GetValue().strip()
        if not url.startswith(("http://", "https://")):
            self._mostrar_vista_previa("Escribe una URL que empiece por http:// o https://")
            # Con el foco en el boton, el aviso aparecia y no lo oia nadie.
            self.vista_previa.SetFocus()
            return

        self.boton_comprobar.Disable()
        self._mostrar_vista_previa("Comprobando…")

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
        self._mostrar_vista_previa(f"No se pudo comprobar: {mensaje}")
        self.vista_previa.SetFocus()

    def _al_completar_comprobacion(self, url: str, metadatos: dict) -> None:
        self.boton_comprobar.Enable()
        self._metadatos = {**metadatos, "url": url}
        titulo = metadatos.get("titulo") or url

        duplicado = buscar_duplicado(self._guardados, url)
        if duplicado:
            self._mostrar_vista_previa(
                f"{titulo}\nYa tienes este enlace guardado: "
                f"{duplicado.titulo or duplicado.url}"
            )
            self.boton_guardar.SetLabel("Guardar de todas &formas")
        else:
            self._mostrar_vista_previa(titulo)
            self.boton_guardar.SetLabel("&Guardar")

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
