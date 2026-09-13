"""Inicio de sesion con Google: abre el navegador del sistema y espera a que
termines, sondeando el servidor (ver login_oauth.py y docs/CONTRATO-API.md).

Aqui la cuenta SI es obligatoria, al reves que en el iPhone: esta app existe
para sincronizar, y sin cuenta no hay con quien.

Sobre el foco, que es lo que manda en esta pantalla: al abrirse el navegador el
foco se va de la aplicacion entera, asi que durante la espera no hay a quien
anunciarle nada. Lo que importa es lo que te encuentras AL VOLVER: si sale
bien, el dialogo ya se ha cerrado solo y esta la ventana principal; si sale
mal, el foco esta puesto en el cuadro de estado, que NVDA lee al recibirlo por
ser un control de texto (un StaticText no puede tener el foco).
"""

from __future__ import annotations

import threading

import wx

from .. import login_oauth
from ..api_cliente import ClienteApi, ErrorApi
from ..sesion import Sesion
from .campos import ESTILO_SOLO_LECTURA, con_etiqueta, mostrar_con_etiqueta

AVISO = (
    "Para sincronizar tus enlaces con el iPhone necesitas entrar con tu cuenta "
    "de Google.\n"
    "Al pulsar Entrar se abre el navegador. Termina ahí y vuelve a esta "
    "ventana: se cerrará sola cuando hayas entrado."
)


class DialogoLogin(wx.Dialog):
    def __init__(self, padre: wx.Window, sesion: Sesion, cliente: ClienteApi):
        super().__init__(padre, title="Iniciar sesión")
        self._sesion = sesion
        self._cliente = cliente
        self._cancelar = threading.Event()
        self._cerrado = False

        self._panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        # Cuadros de solo lectura y no StaticText: a un StaticText no llega el
        # tabulador ni se puede recorrer con las flechas.
        etiqueta_aviso, self.aviso = con_etiqueta(
            self._panel,
            "&Qué hay que hacer:",
            lambda padre: wx.TextCtrl(
                padre, value=AVISO, style=ESTILO_SOLO_LECTURA, size=(420, 80)
            ),
        )
        sizer.Add(etiqueta_aviso, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.aviso, 0, wx.EXPAND | wx.ALL, 12)

        # Oculto hasta que haya algo que contar: vacio era una parada del
        # tabulador en la que las flechas no leian nada.
        etiqueta_estado, self.estado = con_etiqueta(
            self._panel,
            "E&stado:",
            lambda padre: wx.TextCtrl(padre, style=ESTILO_SOLO_LECTURA, size=(420, 60)),
        )
        sizer.Add(etiqueta_estado, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.estado, 0, wx.EXPAND | wx.ALL, 12)
        mostrar_con_etiqueta(self.estado, False)

        botones = wx.StdDialogButtonSizer()
        self.boton_entrar = wx.Button(self._panel, wx.ID_OK, "&Entrar con Google")
        boton_cancelar = wx.Button(self._panel, wx.ID_CANCEL, "&Cancelar")
        self.boton_entrar.SetDefault()
        botones.AddButton(self.boton_entrar)
        botones.AddButton(boton_cancelar)
        botones.Realize()
        sizer.Add(botones, 0, wx.ALIGN_RIGHT | wx.ALL, 12)

        self._panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(self._panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        self.boton_entrar.Bind(wx.EVT_BUTTON, self._al_entrar)
        boton_cancelar.Bind(wx.EVT_BUTTON, self._al_cancelar)
        self.Bind(wx.EVT_CLOSE, self._al_cancelar)

    def ShowModal(self) -> int:
        # El foco se pone con el cuadro ya a la vista. Puesto en el constructor,
        # Windows no lo respeta y el foco cae en el primer control.
        wx.CallAfter(self.boton_entrar.SetFocus)
        return super().ShowModal()

    # --- hilo de la interfaz ---

    def _al_entrar(self, evento: wx.CommandEvent) -> None:
        estado = login_oauth.generar_estado()
        url = self._cliente.url_iniciar_login("google", estado)

        if not login_oauth.abrir_navegador(url):
            self._decir(
                "No se pudo abrir el navegador. Abre esta dirección a mano y "
                f"vuelve aquí:\n{url}"
            )
            return

        self.boton_entrar.Disable()
        self._decir(
            "Esperando a que termines en el navegador. Puedes volver aquí "
            "cuando hayas entrado."
        )
        threading.Thread(target=self._sondear, args=(estado,), daemon=True).start()

    def _al_cancelar(self, evento: wx.Event) -> None:
        self._cancelar.set()
        self._cerrado = True
        self.EndModal(wx.ID_CANCEL)

    # --- hilo de fondo ---

    def _sondear(self, estado: str) -> None:
        """Corre FUERA del hilo de la interfaz: todo lo que toque wx vuelve por
        wx.CallAfter."""
        resultado = login_oauth.esperar_codigo_canje(
            self._cliente, estado, self._cancelar.is_set
        )
        if resultado.estado == "exito":
            try:
                self._sesion.iniciar_con_codigo_canje(resultado.codigo_canje)
            except ErrorApi as error:
                wx.CallAfter(self._al_fallo, str(error))
                return
            wx.CallAfter(self._al_exito)
        elif resultado.estado == "error":
            wx.CallAfter(self._al_fallo, resultado.mensaje)
        # "cancelado": el dialogo ya esta cerrado, no hay a quien contarselo

    # --- vuelta al hilo de la interfaz ---

    def _al_exito(self) -> None:
        if self._cerrado:
            return
        self._cerrado = True
        self.EndModal(wx.ID_OK)

    def _al_fallo(self, mensaje: str) -> None:
        if self._cerrado:
            return
        self.boton_entrar.Enable()
        self._decir(mensaje)

    def _decir(self, mensaje: str) -> None:
        self.estado.SetValue(mensaje)
        mostrar_con_etiqueta(self.estado, True)
        self._panel.Layout()
        self.Fit()
        self.estado.SetFocus()
