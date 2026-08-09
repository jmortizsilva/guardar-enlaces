"""Ventana principal: lista de elementos guardados, busqueda, anadir/
eliminar. wx.ListCtrl en modo report (envuelve el control nativo Win32
SysListView32, que NVDA/JAWS/Narrador anuncian de forma consistente) con
TODO el texto legible de la fila en la columna 0 (ver presentacion.py)."""

from __future__ import annotations

import threading

import wx

from ..almacen_local import AlmacenLocal
from ..api_cliente import ClienteApi, ErrorApi
from ..modelo import Elemento, buscar, elementos_visibles, marcar_borrado
from ..presentacion import texto_fila
from ..sesion import Sesion
from ..sincronizador import Sincronizador
from .bandeja import IconoBandeja
from .dialogo_anadir import DialogoAnadir
from .dialogo_detalle import DialogoDetalle


class VentanaPrincipal(wx.Frame):
    def __init__(self, almacen: AlmacenLocal, cliente: ClienteApi, sesion: Sesion):
        super().__init__(None, title="Guardar enlaces", size=(720, 520))
        self._almacen = almacen
        self._cliente = cliente
        self._sesion = sesion
        self._sincronizador = Sincronizador(almacen, cliente, sesion)
        self._elementos_mostrados: list[Elemento] = []

        self._construir_menu()
        self._construir_controles()
        self._icono_bandeja = IconoBandeja(self)

        self.Bind(wx.EVT_CLOSE, self._al_cerrar)

        self._cargar_desde_cache()
        self.sincronizar_en_segundo_plano()

    # --- construccion ---

    def _construir_menu(self) -> None:
        id_anadir = wx.NewIdRef()
        id_sincronizar = wx.NewIdRef()

        barra = wx.MenuBar()
        menu_archivo = wx.Menu()
        menu_archivo.Append(id_anadir, "&Añadir enlace...\tCtrl+N")
        menu_archivo.Append(id_sincronizar, "&Sincronizar ahora\tF5")
        menu_archivo.AppendSeparator()
        menu_archivo.Append(wx.ID_EXIT, "&Salir\tCtrl+Q")
        barra.Append(menu_archivo, "&Archivo")
        self.SetMenuBar(barra)

        self.Bind(wx.EVT_MENU, self._al_anadir, id=id_anadir)
        self.Bind(wx.EVT_MENU, lambda e: self.sincronizar_en_segundo_plano(), id=id_sincronizar)
        self.Bind(wx.EVT_MENU, lambda e: self.Close(), id=wx.ID_EXIT)

    def _construir_controles(self) -> None:
        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        self.buscador = wx.SearchCtrl(panel)
        self.buscador.SetDescriptiveText("Buscar por título, URL o etiqueta")
        self.buscador.ShowCancelButton(True)
        self.buscador.Bind(wx.EVT_TEXT, self._al_buscar)
        self.buscador.Bind(wx.EVT_SEARCHCTRL_CANCEL_BTN, self._al_cancelar_busqueda)
        sizer.Add(self.buscador, 0, wx.EXPAND | wx.ALL, 8)

        self.lista = wx.ListCtrl(panel, style=wx.LC_REPORT | wx.LC_SINGLE_SEL)
        self.lista.InsertColumn(0, "Enlace guardado", width=680)
        self.lista.Bind(wx.EVT_LIST_ITEM_ACTIVATED, self._al_abrir_seleccionado)
        sizer.Add(self.lista, 1, wx.EXPAND | wx.ALL, 8)

        panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(panel, 1, wx.EXPAND)
        self.SetSizer(marco)

        self.CreateStatusBar()

        id_eliminar = wx.NewIdRef()
        self.SetAcceleratorTable(
            wx.AcceleratorTable([(wx.ACCEL_NORMAL, wx.WXK_DELETE, id_eliminar)])
        )
        self.Bind(wx.EVT_MENU, self._al_eliminar_seleccionado, id=id_eliminar)

    # --- lista ---

    def _cargar_desde_cache(self) -> None:
        cache = self._almacen.cargar_todos()
        consulta = self.buscador.GetValue() if hasattr(self, "buscador") else ""
        self._refrescar_lista(buscar(elementos_visibles(cache), consulta))

    def _refrescar_lista(self, elementos: list[Elemento]) -> None:
        self._elementos_mostrados = elementos
        self.lista.DeleteAllItems()
        for indice, elemento in enumerate(elementos):
            self.lista.InsertItem(indice, texto_fila(elemento))
        n = len(elementos)
        # el StatusBar de Win32 se anuncia solo al cambiar (no hace falta anuncio aparte)
        self.SetStatusText(f"{n} elemento{'s' if n != 1 else ''}")

    def _elemento_en(self, indice: int) -> Elemento | None:
        if 0 <= indice < len(self._elementos_mostrados):
            return self._elementos_mostrados[indice]
        return None

    def _al_buscar(self, evento: wx.CommandEvent) -> None:
        self._cargar_desde_cache()

    def _al_cancelar_busqueda(self, evento: wx.CommandEvent) -> None:
        self.buscador.SetValue("")
        self._cargar_desde_cache()

    # --- anadir / abrir / eliminar ---

    def _al_anadir(self, evento: wx.CommandEvent) -> None:
        dialogo = DialogoAnadir(self, self._cliente, self._sesion)
        if dialogo.ShowModal() == wx.ID_OK and dialogo.elemento_creado:
            elemento = dialogo.elemento_creado
            self._almacen.marcar_pendiente(elemento)
            self._cargar_desde_cache()
            self.SetStatusText(f"Añadido: {elemento.titulo or elemento.url}")
            self.sincronizar_en_segundo_plano()
        dialogo.Destroy()

    def _al_abrir_seleccionado(self, evento: wx.ListEvent) -> None:
        elemento = self._elemento_en(evento.GetIndex())
        if not elemento:
            return
        dialogo = DialogoDetalle(self, elemento, self._al_elemento_editado, self._al_elemento_eliminado)
        dialogo.ShowModal()
        dialogo.Destroy()

    def _al_eliminar_seleccionado(self, evento: wx.CommandEvent) -> None:
        indice = self.lista.GetFirstSelected()
        elemento = self._elemento_en(indice)
        if not elemento:
            return
        titulo = elemento.titulo or elemento.url
        if wx.MessageBox(
            f"¿Eliminar «{titulo}»?", "Confirmar eliminación", wx.YES_NO | wx.ICON_QUESTION, self
        ) == wx.YES:
            self._al_elemento_eliminado(elemento)

    def _al_elemento_editado(self, elemento: Elemento) -> None:
        self._almacen.marcar_pendiente(elemento)
        self._cargar_desde_cache()
        self.sincronizar_en_segundo_plano()

    def _al_elemento_eliminado(self, elemento: Elemento) -> None:
        borrado = marcar_borrado(elemento)
        self._almacen.marcar_pendiente(borrado)
        self._cargar_desde_cache()
        self.SetStatusText(f"Eliminado: {elemento.titulo or elemento.url}")
        self.sincronizar_en_segundo_plano()

    # --- sincronizacion ---

    def sincronizar_en_segundo_plano(self) -> None:
        def trabajo() -> None:
            try:
                self._sincronizador.sincronizar()
            except ErrorApi as error:
                wx.CallAfter(self.SetStatusText, f"No se pudo sincronizar: {error}")
                return
            wx.CallAfter(self._cargar_desde_cache)

        threading.Thread(target=trabajo, daemon=True).start()

    # --- cierre ---

    def _al_cerrar(self, evento: wx.CloseEvent) -> None:
        self._icono_bandeja.Destroy()
        self.Destroy()
