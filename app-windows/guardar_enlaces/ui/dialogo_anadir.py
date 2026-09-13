"""Anadir un enlace: pegar la URL, escribir etiquetas si se quiere, y
"Guardar". La comprobacion (llamar a /metadatos para sacar titulo y
descripcion) ya no es un paso aparte: pasa a formar parte de Guardar, en
segundo plano, y si falla se guarda igual solo con la URL -- comprobar
nunca fue lo importante, guardar el enlace si.

Si la URL ya esta guardada, no se duplica: se pregunta y, si se acepta, se
ACTUALIZA ese mismo enlace con lo que diga la comprobacion de ahora (y se
fusionan las etiquetas nuevas con las que ya tenia, sin perder ninguna)."""

from __future__ import annotations

import threading

import wx

from ..api_cliente import ClienteApi, ErrorApi
from ..duplicados import buscar_duplicado
from ..modelo import Elemento, editar, nuevo_elemento_local
from ..sesion import Sesion
from .campos import ESTILO_SOLO_LECTURA, con_etiqueta, mostrar_con_etiqueta
from .preguntas import confirmar_guardar_duplicado


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
        self._cerrado = False
        self.elemento_creado: Elemento | None = None
        # True si elemento_creado sustituye a un enlace ya guardado (misma
        # url), en vez de ser un alta nueva -- para poder decir "Actualizado"
        # y no "Añadido" en el aviso.
        self.actualizado_existente = False

        self._panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        etiqueta_url, self.campo_url = con_etiqueta(self._panel, "&URL:", wx.TextCtrl)
        self.campo_url.SetHint("https://...")
        sizer.Add(etiqueta_url, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.campo_url, 0, wx.EXPAND | wx.ALL, 12)

        etiqueta_etiquetas, self.campo_etiquetas = con_etiqueta(
            self._panel, "Eti&quetas, separadas por comas:", wx.TextCtrl
        )
        sizer.Add(etiqueta_etiquetas, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.campo_etiquetas, 0, wx.EXPAND | wx.ALL, 12)

        # Cuadro de solo lectura y no StaticText, para poder leerlo con las
        # flechas. Oculto mientras no haya nada que contar (ver "Guardando..."
        # y los fallos mas abajo).
        etiqueta_estado, self.estado = con_etiqueta(
            self._panel,
            "&Estado:",
            lambda padre: wx.TextCtrl(padre, style=ESTILO_SOLO_LECTURA, size=(420, 60)),
        )
        sizer.Add(etiqueta_estado, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.estado, 0, wx.EXPAND | wx.ALL, 12)
        mostrar_con_etiqueta(self.estado, False)

        botones = wx.StdDialogButtonSizer()
        self.boton_guardar = wx.Button(self._panel, wx.ID_OK, "&Guardar")
        boton_cancelar = wx.Button(self._panel, wx.ID_CANCEL, "&Cancelar")
        botones.AddButton(self.boton_guardar)
        botones.AddButton(boton_cancelar)
        botones.Realize()
        sizer.Add(botones, 0, wx.ALIGN_RIGHT | wx.ALL, 12)

        self._panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(self._panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        self.boton_guardar.Bind(wx.EVT_BUTTON, self._al_guardar)
        boton_cancelar.Bind(wx.EVT_BUTTON, self._al_cancelar)
        self.campo_url.SetFocus()

    def _al_cancelar(self, evento: wx.CommandEvent) -> None:
        # Guardar puede seguir esperando la red cuando se cancela: sin este
        # aviso, el hilo de fondo llamaria a wx.CallAfter sobre un dialogo ya
        # destruido al terminar.
        self._cerrado = True
        self.EndModal(wx.ID_CANCEL)

    def _decir(self, mensaje: str) -> None:
        self.estado.SetValue(mensaje)
        mostrar_con_etiqueta(self.estado, True)
        self._panel.Layout()
        self.Fit()
        self.estado.SetFocus()

    def _al_guardar(self, evento: wx.CommandEvent) -> None:
        url = self.campo_url.GetValue().strip()
        if not url.startswith(("http://", "https://")):
            self._decir("Escribe una URL que empiece por http:// o https://")
            return

        duplicado = buscar_duplicado(self._guardados, url)
        if duplicado and not confirmar_guardar_duplicado(
            duplicado.titulo or duplicado.url, self
        ):
            return

        etiquetas = tuple(
            e.strip() for e in self.campo_etiquetas.GetValue().split(",") if e.strip()
        )

        self.boton_guardar.Disable()
        self._decir("Guardando…")

        def trabajo() -> None:
            # La comprobacion es lo de menos: si falla (sin red, sitio caido),
            # el enlace se guarda igual, solo que sin titulo ni descripcion.
            try:
                metadatos = self._sesion.con_reintento(
                    lambda token: self._cliente.metadatos(url, token)
                )
            except ErrorApi:
                metadatos = {}
            wx.CallAfter(self._al_completar_guardado, url, metadatos, etiquetas, duplicado)

        threading.Thread(target=trabajo, daemon=True).start()

    def _al_completar_guardado(
        self,
        url: str,
        metadatos: dict,
        etiquetas: tuple[str, ...],
        duplicado: Elemento | None,
    ) -> None:
        if self._cerrado:
            return
        self._cerrado = True
        if duplicado:
            # Actualiza el enlace que ya habia, no crea uno nuevo. Si la
            # comprobacion fallo (metadatos vacios), se conserva lo que ya
            # tenia en vez de borrarlo. Las etiquetas se fusionan, sin perder
            # las que ya llevaba.
            self.actualizado_existente = True
            self.elemento_creado = editar(
                duplicado,
                titulo=metadatos.get("titulo") or duplicado.titulo,
                descripcion=metadatos.get("descripcion") or duplicado.descripcion,
                imagen_url=metadatos.get("imagenUrl") or duplicado.imagen_url,
                tipo=metadatos.get("tipo") or duplicado.tipo,
                etiquetas=tuple(dict.fromkeys((*duplicado.etiquetas, *etiquetas))),
            )
        else:
            self.elemento_creado = nuevo_elemento_local(
                url=url,
                titulo=metadatos.get("titulo"),
                descripcion=metadatos.get("descripcion"),
                imagen_url=metadatos.get("imagenUrl"),
                tipo=metadatos.get("tipo") or "enlace",
                etiquetas=etiquetas,
            )
        self.EndModal(wx.ID_OK)
