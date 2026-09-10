"""Ventana principal: lista de elementos guardados, busqueda, filtro por
etiqueta, anadir/eliminar. wx.ListCtrl en modo report (envuelve el control
nativo Win32 SysListView32, que NVDA/JAWS/Narrador anuncian de forma
consistente) con TODO el texto legible de la fila en la columna 0 (ver
presentacion.py).

Las acciones sobre un elemento (abrir, copiar URL, editar etiquetas,
eliminar) se sacan por menu contextual: wx.EVT_CONTEXT_MENU cubre a la vez
el clic derecho del raton, el teclado (tecla Menu/Aplicaciones) y
Mayus+F10, que es como un usuario de NVDA/JAWS lo espera sin tener que
usar el raton.
"""

from __future__ import annotations

import threading
import webbrowser

import wx

from ..almacen_local import AlmacenLocal
from ..api_cliente import ClienteApi, ErrorApi
from ..modelo import (
    Elemento,
    buscar,
    elementos_visibles,
    etiquetas_disponibles,
    filtrar_por_etiqueta,
    marcar_borrado,
)
from ..presentacion import texto_fila
from ..sesion import Sesion
from ..sincronizador import Sincronizador
from .bandeja import IconoBandeja
from .dialogo_anadir import DialogoAnadir
from .dialogo_detalle import DialogoDetalle

_TODAS_LAS_ETIQUETAS = "(todas las etiquetas)"


class VentanaPrincipal(wx.Frame):
    def __init__(self, almacen: AlmacenLocal, cliente: ClienteApi, sesion: Sesion):
        super().__init__(None, title="Guardar enlaces", size=(760, 520))
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

        fila_filtros = wx.BoxSizer(wx.HORIZONTAL)

        # El wx.StaticText de al lado es solo para quien ve la pantalla: desde wxPython 4.0.4,
        # NVDA/Narrador NO infieren el nombre accesible de un control por estar al lado de una
        # etiqueta ni por su texto de sugerencia (SetDescriptiveText/SetHint) -- hace falta
        # SetName() explicito (o el kwarg name= del constructor), o el control se anuncia con un
        # rotulo generico ("edicion"). Ver docs/ACCESIBILIDAD-WXPYTHON.md.
        etiqueta_buscar = wx.StaticText(panel, label="&Buscar:")
        fila_filtros.Add(etiqueta_buscar, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 4)
        self.buscador = wx.SearchCtrl(panel)
        self.buscador.SetName("Buscar por título, URL o etiqueta")
        self.buscador.SetDescriptiveText("Título, URL o etiqueta")
        self.buscador.ShowCancelButton(True)
        self.buscador.Bind(wx.EVT_TEXT, self._al_cambiar_filtro)
        self.buscador.Bind(wx.EVT_SEARCHCTRL_CANCEL_BTN, self._al_cancelar_busqueda)
        fila_filtros.Add(self.buscador, 1, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 12)

        etiqueta_filtro = wx.StaticText(panel, label="&Etiqueta:")
        fila_filtros.Add(etiqueta_filtro, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 4)
        self.selector_etiqueta = wx.Choice(panel, choices=[_TODAS_LAS_ETIQUETAS])
        self.selector_etiqueta.SetName("Filtrar por etiqueta")
        self.selector_etiqueta.SetSelection(0)
        self.selector_etiqueta.Bind(wx.EVT_CHOICE, self._al_cambiar_filtro)
        fila_filtros.Add(self.selector_etiqueta, 0, wx.ALIGN_CENTER_VERTICAL)

        sizer.Add(fila_filtros, 0, wx.EXPAND | wx.ALL, 8)

        self.lista = wx.ListCtrl(panel, style=wx.LC_REPORT | wx.LC_SINGLE_SEL)
        self.lista.InsertColumn(0, "Enlace guardado", width=720)
        self.lista.Bind(wx.EVT_LIST_ITEM_ACTIVATED, self._al_abrir_seleccionado)
        self.lista.Bind(wx.EVT_CONTEXT_MENU, self._al_menu_contextual)
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

    # --- lista: cargar, filtrar, refrescar ---

    def _cargar_desde_cache(self) -> None:
        cache = self._almacen.cargar_todos()
        self._actualizar_opciones_etiqueta(elementos_visibles(cache))
        self._aplicar_filtros(cache)

    def _actualizar_opciones_etiqueta(self, elementos: list[Elemento]) -> None:
        seleccionada = self._etiqueta_seleccionada()
        opciones = [_TODAS_LAS_ETIQUETAS, *etiquetas_disponibles(elementos)]
        if [self.selector_etiqueta.GetString(i) for i in range(self.selector_etiqueta.GetCount())] == opciones:
            return  # evita parpadeo/perdida de foco si no ha cambiado nada
        self.selector_etiqueta.Set(opciones)
        indice = opciones.index(seleccionada) if seleccionada in opciones else 0
        self.selector_etiqueta.SetSelection(indice)

    def _etiqueta_seleccionada(self) -> str | None:
        texto = self.selector_etiqueta.GetStringSelection()
        return None if not texto or texto == _TODAS_LAS_ETIQUETAS else texto

    def _aplicar_filtros(self, cache: dict[str, Elemento] | None = None) -> None:
        cache = cache if cache is not None else self._almacen.cargar_todos()
        elementos = elementos_visibles(cache)
        elementos = filtrar_por_etiqueta(elementos, self._etiqueta_seleccionada())
        elementos = buscar(elementos, self.buscador.GetValue())
        self._refrescar_lista(elementos)

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

    def _al_cambiar_filtro(self, evento: wx.Event) -> None:
        self._aplicar_filtros()

    def _al_cancelar_busqueda(self, evento: wx.CommandEvent) -> None:
        self.buscador.SetValue("")
        self._aplicar_filtros()

    # --- anadir / abrir / menu contextual / eliminar ---

    def _al_anadir(self, evento: wx.CommandEvent) -> None:
        # Los ya guardados van al dialogo para poder avisar de repetidos.
        guardados = elementos_visibles(self._almacen.cargar_todos())
        dialogo = DialogoAnadir(self, self._cliente, self._sesion, guardados)
        if dialogo.ShowModal() == wx.ID_OK and dialogo.elemento_creado:
            elemento = dialogo.elemento_creado
            self._almacen.marcar_pendiente(elemento)
            self._cargar_desde_cache()
            self.SetStatusText(f"Añadido: {elemento.titulo or elemento.url}")
            self.sincronizar_en_segundo_plano()
        dialogo.Destroy()

    def _al_abrir_seleccionado(self, evento: wx.ListEvent) -> None:
        elemento = self._elemento_en(evento.GetIndex())
        if elemento:
            self._mostrar_detalle(elemento)

    def _mostrar_detalle(self, elemento: Elemento) -> None:
        dialogo = DialogoDetalle(self, elemento, self._al_elemento_editado, self._al_elemento_eliminado)
        dialogo.ShowModal()
        dialogo.Destroy()

    def _al_menu_contextual(self, evento: wx.ContextMenuEvent) -> None:
        posicion = evento.GetPosition()
        if posicion == wx.DefaultPosition:
            # abierto por teclado (tecla Menu o Mayus+F10): actua sobre el elemento con foco
            indice = self.lista.GetFirstSelected()
        else:
            punto_cliente = self.lista.ScreenToClient(posicion)
            indice, _ = self.lista.HitTest(punto_cliente)
            if indice != -1:
                self.lista.Select(indice)
                self.lista.Focus(indice)

        elemento = self._elemento_en(indice)
        if not elemento:
            return

        menu = wx.Menu()
        item_abrir = menu.Append(wx.ID_ANY, "&Abrir en el navegador")
        item_copiar = menu.Append(wx.ID_ANY, "&Copiar URL")
        item_editar = menu.Append(wx.ID_ANY, "&Editar etiquetas...")
        menu.AppendSeparator()
        item_eliminar = menu.Append(wx.ID_ANY, "&Eliminar")

        self.Bind(wx.EVT_MENU, lambda e: webbrowser.open(elemento.url), item_abrir)
        self.Bind(wx.EVT_MENU, lambda e: self._copiar_url(elemento), item_copiar)
        self.Bind(wx.EVT_MENU, lambda e: self._mostrar_detalle(elemento), item_editar)
        self.Bind(wx.EVT_MENU, lambda e: self._confirmar_y_eliminar(elemento), item_eliminar)

        self.PopupMenu(menu)
        menu.Destroy()

    def _copiar_url(self, elemento: Elemento) -> None:
        if wx.TheClipboard.Open():
            wx.TheClipboard.SetData(wx.TextDataObject(elemento.url))
            wx.TheClipboard.Close()
        self.SetStatusText(f"URL copiada: {elemento.url}")

    def _al_eliminar_seleccionado(self, evento: wx.CommandEvent) -> None:
        elemento = self._elemento_en(self.lista.GetFirstSelected())
        if elemento:
            self._confirmar_y_eliminar(elemento)

    def _confirmar_y_eliminar(self, elemento: Elemento) -> None:
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
                rechazados = self._sincronizador.sincronizar()
            except ErrorApi as error:
                wx.CallAfter(self.SetStatusText, f"No se pudo sincronizar: {error}")
                return
            wx.CallAfter(self._cargar_desde_cache)
            if rechazados:
                # _cargar_desde_cache deja el numero de elementos en la barra, asi
                # que esto va despues para que no lo pise. La barra de estado de
                # Win32 se anuncia sola al cambiar (docs/ACCESIBILIDAD-WXPYTHON.md).
                wx.CallAfter(
                    self.SetStatusText,
                    f"{rechazados} cambio{'s' if rechazados != 1 else ''} no se "
                    "pudo subir al servidor",
                )

        threading.Thread(target=trabajo, daemon=True).start()

    # --- cierre ---

    def _al_cerrar(self, evento: wx.CloseEvent) -> None:
        self._icono_bandeja.Destroy()
        self.Destroy()
