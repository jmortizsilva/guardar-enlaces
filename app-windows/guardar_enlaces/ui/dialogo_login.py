"""Inicio de sesion DE DESARROLLO: pide un correo ya invitado y llama a
POST /auth/dev-login (solo funciona si el servidor tiene
PERMITIR_LOGIN_DEV=true). Sustituye temporalmente al flujo real con Google/
Apple (ver docs/CONTRATO-API.md) mientras no haya credenciales OAuth
registradas; se sustituira por login_oauth.py (navegador del sistema +
sondeo de /auth/estado) en una fase posterior, sin tocar el resto de la app:
Sesion.iniciar_con_dev_login ya vive detras de la misma interfaz que usara
el login real.
"""

from __future__ import annotations

import threading

import wx

from ..api_cliente import ErrorApi
from ..sesion import Sesion


class DialogoLogin(wx.Dialog):
    def __init__(self, padre: wx.Window, sesion: Sesion):
        super().__init__(padre, title="Iniciar sesión (modo desarrollo)")
        self._sesion = sesion

        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        aviso = wx.StaticText(
            panel,
            label="Modo de desarrollo: escribe un correo ya invitado.\n"
            "No pasa por Google ni Apple todavía.",
        )
        sizer.Add(aviso, 0, wx.ALL, 12)

        etiqueta_correo = wx.StaticText(panel, label="&Correo electrónico:")
        sizer.Add(etiqueta_correo, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        self.campo_correo = wx.TextCtrl(panel)
        # SetName(), no basta con el StaticText de al lado ni con SetHint (ver
        # docs/ACCESIBILIDAD-WXPYTHON.md): sin esto NVDA anuncia "edicion" a secas.
        self.campo_correo.SetName("Correo electrónico")
        self.campo_correo.SetHint("persona@ejemplo.com")
        sizer.Add(self.campo_correo, 0, wx.EXPAND | wx.ALL, 12)

        self.etiqueta_error = wx.StaticText(panel, label="")
        self.etiqueta_error.SetForegroundColour(wx.Colour(178, 34, 34))
        sizer.Add(self.etiqueta_error, 0, wx.LEFT | wx.RIGHT, 12)

        botones = wx.StdDialogButtonSizer()
        self.boton_entrar = wx.Button(panel, wx.ID_OK, "&Entrar")
        boton_cancelar = wx.Button(panel, wx.ID_CANCEL, "Cancelar")
        self.boton_entrar.SetDefault()
        botones.AddButton(self.boton_entrar)
        botones.AddButton(boton_cancelar)
        botones.Realize()
        sizer.Add(botones, 0, wx.ALIGN_RIGHT | wx.ALL, 12)

        panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        self.boton_entrar.Bind(wx.EVT_BUTTON, self._al_entrar)
        self.campo_correo.SetFocus()

    def _al_entrar(self, evento: wx.CommandEvent) -> None:
        correo = self.campo_correo.GetValue().strip()
        if "@" not in correo:
            self._mostrar_error("Escribe un correo válido.")
            return

        self.boton_entrar.Disable()
        self.etiqueta_error.SetLabel("")

        def trabajo() -> None:
            try:
                self._sesion.iniciar_con_dev_login(correo)
            except ErrorApi as error:
                wx.CallAfter(self._mostrar_error, str(error))
                return
            wx.CallAfter(self._al_exito)

        threading.Thread(target=trabajo, daemon=True).start()

    def _al_exito(self) -> None:
        self.EndModal(wx.ID_OK)

    def _mostrar_error(self, mensaje: str) -> None:
        self.boton_entrar.Enable()
        self.etiqueta_error.SetLabel(mensaje)
        self.etiqueta_error.GetParent().Layout()
        self.campo_correo.SetFocus()
