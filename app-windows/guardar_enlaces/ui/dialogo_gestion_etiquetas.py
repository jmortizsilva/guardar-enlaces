"""Gestor de etiquetas: renombrar o eliminar una etiqueta en TODOS los
enlaces que la llevan de una vez, o crear una nueva antes de que ningun
enlace la use (una "etiqueta reservada", ver modelo.EtiquetaDefinida)."""

from __future__ import annotations

from typing import Callable, Iterable

import wx

from ..modelo import Elemento, EtiquetaDefinida, etiquetas_disponibles, recuento_por_etiqueta
from .campos import con_etiqueta
from .preguntas import avisar, confirmar_eliminar_etiqueta


class DialogoNombreEtiqueta(wx.Dialog):
    """Pide un nombre de etiqueta. Sirve tanto para renombrar (valor inicial
    = la etiqueta actual, sin prohibidos: fusionar con una que ya existe es
    valido) como para anadir una nueva (valor inicial vacio, con los nombres
    que ya existen como prohibidos: "nueva" deja de significar algo si crea
    un duplicado)."""

    def __init__(
        self,
        padre: wx.Window,
        titulo: str,
        texto_boton: str,
        valor_inicial: str = "",
        nombres_prohibidos: Iterable[str] | None = None,
    ):
        super().__init__(padre, title=titulo)
        self.nombre: str | None = None
        self._prohibidos = frozenset(nombres_prohibidos or ())

        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        etiqueta_campo, self.campo_nombre = con_etiqueta(panel, "&Nombre:", wx.TextCtrl)
        self.campo_nombre.SetValue(valor_inicial)
        sizer.Add(etiqueta_campo, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.campo_nombre, 0, wx.EXPAND | wx.ALL, 12)

        botones = wx.StdDialogButtonSizer()
        self.boton_guardar = wx.Button(panel, wx.ID_OK, texto_boton)
        boton_cancelar = wx.Button(panel, wx.ID_CANCEL, "Cancelar")
        botones.AddButton(self.boton_guardar)
        botones.AddButton(boton_cancelar)
        botones.Realize()
        sizer.Add(botones, 0, wx.ALIGN_RIGHT | wx.ALL, 12)

        panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        self.boton_guardar.Bind(wx.EVT_BUTTON, self._al_guardar)
        self.campo_nombre.SetFocus()
        self.campo_nombre.SelectAll()

    def _al_guardar(self, evento: wx.CommandEvent) -> None:
        nombre = self.campo_nombre.GetValue().strip()
        if not nombre:
            avisar("El nombre no puede quedar vacío.", self.GetTitle(), self)
            return
        if nombre in self._prohibidos:
            avisar(f"Ya existe una etiqueta «{nombre}».", self.GetTitle(), self)
            return
        self.nombre = nombre
        self.EndModal(wx.ID_OK)


class DialogoGestionEtiquetas(wx.Dialog):
    def __init__(
        self,
        padre: wx.Window,
        obtener_elementos: Callable[[], list[Elemento]],
        obtener_reservadas: Callable[[], list[EtiquetaDefinida]],
        al_renombrar: Callable[[str, str], None],
        al_eliminar: Callable[[str], None],
        al_anadir: Callable[[str], None],
    ):
        super().__init__(padre, title="Gestionar etiquetas", size=(420, 400))
        self._obtener_elementos = obtener_elementos
        self._obtener_reservadas = obtener_reservadas
        self._al_renombrar = al_renombrar
        self._al_eliminar = al_eliminar
        self._al_anadir = al_anadir
        self._etiquetas: list[str] = []

        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        # Sin cabecera: con una sola columna no dice nada que no diga la etiqueta.
        etiqueta_lista, self.lista = con_etiqueta(
            panel,
            "&Etiquetas:",
            lambda p: wx.ListCtrl(p, style=wx.LC_REPORT | wx.LC_SINGLE_SEL | wx.LC_NO_HEADER),
        )
        self.lista.InsertColumn(0, "Etiqueta", width=380)
        self.lista.Bind(wx.EVT_CONTEXT_MENU, self._al_menu_contextual)
        # Suprimir SOLO en esta lista, no en todo el dialogo (ver
        # docs/ACCESIBILIDAD-WXPYTHON.md #8): un AcceleratorTable del dialogo
        # borraria la etiqueta activa aunque el foco estuviera en otro sitio.
        self.lista.Bind(wx.EVT_KEY_DOWN, self._al_tecla_en_lista)
        sizer.Add(etiqueta_lista, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.lista, 1, wx.EXPAND | wx.ALL, 12)

        botones = wx.BoxSizer(wx.HORIZONTAL)
        self.boton_anadir = wx.Button(panel, label="&Añadir etiqueta nueva...")
        self.boton_cerrar = wx.Button(panel, wx.ID_CLOSE, "&Cerrar")
        botones.Add(self.boton_anadir, 0, wx.RIGHT, 8)
        botones.Add(self.boton_cerrar, 0)
        sizer.Add(botones, 0, wx.ALIGN_RIGHT | wx.ALL, 12)
        self.SetEscapeId(wx.ID_CLOSE)

        panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(panel, 1, wx.EXPAND)
        self.SetSizer(marco)

        self.boton_anadir.Bind(wx.EVT_BUTTON, lambda evento: self._anadir())
        self.boton_cerrar.Bind(wx.EVT_BUTTON, lambda evento: self.EndModal(wx.ID_CLOSE))

        self._refrescar()

    def _refrescar(self) -> None:
        elementos = self._obtener_elementos()
        reservadas = {e.nombre for e in self._obtener_reservadas() if not e.borrado}
        self._etiquetas = sorted(set(etiquetas_disponibles(elementos)) | reservadas)
        recuento = recuento_por_etiqueta(elementos)
        self.lista.DeleteAllItems()
        for indice, etiqueta in enumerate(self._etiquetas):
            n = recuento.get(etiqueta, 0)
            cuenta = "1 enlace" if n == 1 else f"{n} enlaces"
            self.lista.InsertItem(indice, f"{etiqueta} ({cuenta})")

    def _etiqueta_en(self, indice: int) -> str | None:
        if 0 <= indice < len(self._etiquetas):
            return self._etiquetas[indice]
        return None

    def _al_tecla_en_lista(self, evento: wx.KeyEvent) -> None:
        if evento.GetKeyCode() == wx.WXK_DELETE:
            self._eliminar(self._etiqueta_en(self.lista.GetFirstSelected()))
        else:
            evento.Skip()

    def _al_menu_contextual(self, evento: wx.ContextMenuEvent) -> None:
        posicion = evento.GetPosition()
        if posicion == wx.DefaultPosition:
            indice = self.lista.GetFirstSelected()
        else:
            punto_cliente = self.lista.ScreenToClient(posicion)
            indice, _ = self.lista.HitTest(punto_cliente)
            if indice != -1:
                self.lista.Select(indice)
                self.lista.Focus(indice)

        etiqueta = self._etiqueta_en(indice)
        if not etiqueta:
            return

        menu = wx.Menu()
        item_renombrar = menu.Append(wx.ID_ANY, "&Renombrar...")
        item_eliminar = menu.Append(wx.ID_ANY, "&Eliminar")

        self.Bind(wx.EVT_MENU, lambda e: self._renombrar(etiqueta), item_renombrar)
        self.Bind(wx.EVT_MENU, lambda e: self._eliminar(etiqueta), item_eliminar)

        self.PopupMenu(menu)
        menu.Destroy()

    def _renombrar(self, etiqueta: str) -> None:
        dialogo = DialogoNombreEtiqueta(
            self, f"Renombrar «{etiqueta}»", "&Guardar", valor_inicial=etiqueta
        )
        if dialogo.ShowModal() == wx.ID_OK and dialogo.nombre:
            self._al_renombrar(etiqueta, dialogo.nombre)
            self._refrescar()
        dialogo.Destroy()

    def _eliminar(self, etiqueta: str | None) -> None:
        if not etiqueta:
            return
        n = recuento_por_etiqueta(self._obtener_elementos()).get(etiqueta, 0)
        if confirmar_eliminar_etiqueta(etiqueta, n, self):
            self._al_eliminar(etiqueta)
            self._refrescar()

    def _anadir(self) -> None:
        dialogo = DialogoNombreEtiqueta(
            self, "Añadir etiqueta", "&Añadir", nombres_prohibidos=self._etiquetas
        )
        if dialogo.ShowModal() == wx.ID_OK and dialogo.nombre:
            self._al_anadir(dialogo.nombre)
            self._refrescar()
        dialogo.Destroy()
