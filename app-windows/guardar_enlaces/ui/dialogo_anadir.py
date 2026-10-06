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

from ..api_cliente import ClienteApi
from ..duplicados import buscar_duplicado
from ..modelo import Elemento, editar, nuevo_elemento_local
from ..direcciones import NO_CARGA, DireccionComprobada, completar, comprobar_direccion
from ..presentacion import MARCADOR_URL, TEXTO_URL_NO_VALIDA
from ..resolver_metadatos import comprobacion_del_servidor, comprobar_en_este_equipo, hay_red
from ..sesion import Sesion
from .campos import ESTILO_SOLO_LECTURA, con_etiqueta, mostrar_con_etiqueta
from .preguntas import confirmar_guardar_duplicado, confirmar_guardar_sin_cargar
from .selector_etiquetas import SelectorEtiquetas


class DialogoAnadir(wx.Dialog):
    def __init__(
        self,
        padre: wx.Window,
        cliente: ClienteApi,
        sesion: Sesion,
        guardados: list[Elemento] | None = None,
        etiquetas_disponibles: list[str] | tuple[str, ...] = (),
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
        self.campo_url.SetHint(MARCADOR_URL)
        sizer.Add(etiqueta_url, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.campo_url, 0, wx.EXPAND | wx.ALL, 12)

        self.etiquetas = SelectorEtiquetas(self._panel, etiquetas_disponibles)
        sizer.Add(self.etiquetas, 0, wx.EXPAND)

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
        escrito = self.campo_url.GetValue().strip()
        # Sin https:// tambien vale: si falta, se pone (ANADIR.md).
        escrita = completar(escrito)
        if escrita is None:
            self._decir(TEXTO_URL_NO_VALIDA)
            return

        duplicado = buscar_duplicado(self._guardados, escrita.direccion)
        if duplicado and not confirmar_guardar_duplicado(
            duplicado.titulo or duplicado.url, self
        ):
            return

        etiquetas = self.etiquetas.etiquetas_elegidas()

        self.boton_guardar.Disable()
        self._decir("Guardando…")

        def comprobar_una(url: str):
            if self._sesion.autenticado:
                # Con cuenta lo dice el servidor, que es quien guarda los
                # metadatos para todos los clientes.
                return comprobacion_del_servidor(
                    lambda: self._sesion.con_reintento(
                        lambda token: self._cliente.metadatos(url, token)
                    )
                )
            # Sin cuenta lo comprueba este mismo equipo, como hace el
            # telefono. Mismas reglas y misma forma de diccionario, para que
            # el enlace no dependa de quien lo resolvio: ver metadatos.py.
            return comprobar_en_este_equipo(
                url, hay_red=lambda: hay_red(self._cliente.url_base)
            )

        def trabajo() -> None:
            comprobada = comprobar_direccion(escrita, comprobar_una)
            wx.CallAfter(self._al_comprobar, escrito, comprobada, etiquetas, duplicado)

        threading.Thread(target=trabajo, daemon=True).start()

    def _al_comprobar(
        self,
        escrito: str,
        comprobada: DireccionComprobada,
        etiquetas: tuple[str, ...],
        duplicado: Elemento | None,
    ) -> None:
        """Si no carga se pregunta antes de guardar; si carga, o no se ha
        podido saber (sin red), se guarda sin mas."""
        if self._cerrado:
            return
        if comprobada.comprobacion.estado == NO_CARGA:
            if not confirmar_guardar_sin_cargar(escrito, self):
                # De vuelta al campo, que es donde esta lo que haya que
                # corregir. El «Guardando…» ya no es verdad.
                mostrar_con_etiqueta(self.estado, False)
                self._panel.Layout()
                self.boton_guardar.Enable()
                self.campo_url.SetFocus()
                return
        self._al_completar_guardado(
            comprobada.direccion, comprobada.comprobacion.metadatos, etiquetas, duplicado
        )

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
