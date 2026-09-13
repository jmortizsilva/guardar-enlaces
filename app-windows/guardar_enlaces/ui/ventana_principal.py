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

import logging
import tempfile
import threading
import time
import webbrowser
from pathlib import Path

import wx

from .. import actualizaciones
from ..almacen_local import AlmacenLocal
from ..api_cliente import ClienteApi, ErrorApi
from ..asentar_cuenta import asentar_cuenta, identidad_dueno
from ..modelo import (
    Elemento,
    buscar,
    elementos_visibles,
    etiquetas_disponibles,
    filtrar_por_etiqueta,
    marcar_borrado,
)
from ..presentacion import texto_fila
from ..seleccion import fila_tras_refrescar
from ..sesion import Sesion
from ..sincronizador import Sincronizador, toca_sincronizar
from ..version import VERSION
from .bandeja import IconoBandeja
from .campos import con_etiqueta
from .dialogo_anadir import DialogoAnadir
from .dialogo_detalle import DialogoDetalle
from .dialogo_login import DialogoLogin
from .preguntas import avisar, confirmar_eliminacion, preguntar_importacion

_TODAS_LAS_ETIQUETAS = "(todas las etiquetas)"


def _titulo_con_cuenta(sesion: Sesion) -> str:
    """La cuenta va en el TITULO, no en la barra de estado: el titulo lo
    anuncia el lector de pantalla al entrar en la ventana, y la barra se pisa
    con cada mensaje. No saber con que cuenta estabas convirtio un "faltan
    enlaces" en una tarde de diagnostico."""
    correo = (sesion.usuario or {}).get("email")
    return f"Guardar enlaces — {correo}" if correo else "Guardar enlaces"


class VentanaPrincipal(wx.Frame):
    def __init__(self, almacen: AlmacenLocal, cliente: ClienteApi, sesion: Sesion):
        super().__init__(None, title=_titulo_con_cuenta(sesion), size=(760, 520))
        self._almacen = almacen
        self._cliente = cliente
        self._sesion = sesion
        self._sincronizador = Sincronizador(almacen, cliente, sesion)
        self._ultima_sincronizacion = 0.0
        self._elementos_mostrados: list[Elemento] = []
        self._filas: list[tuple[str, str]] = []  # (id, texto) de lo que hay en la lista

        self._construir_menu()
        self._construir_controles()
        self._icono_bandeja = IconoBandeja(self)

        self.Bind(wx.EVT_CLOSE, self._al_cerrar)
        self.Bind(wx.EVT_ACTIVATE, self._al_activar)

        self._cargar_desde_cache()
        self.sincronizar_en_segundo_plano()
        self._buscar_actualizaciones_en_segundo_plano(manual=False)

    # --- construccion ---

    def _construir_menu(self) -> None:
        id_anadir = wx.NewIdRef()
        id_sincronizar = wx.NewIdRef()
        id_cerrar_sesion = wx.NewIdRef()
        id_actualizar = wx.NewIdRef()

        barra = wx.MenuBar()
        menu_archivo = wx.Menu()
        menu_archivo.Append(id_anadir, "&Añadir enlace...\tCtrl+N")
        menu_archivo.Append(id_sincronizar, "Si&ncronizar ahora\tF5")
        menu_archivo.AppendSeparator()
        menu_archivo.Append(id_actualizar, "Buscar act&ualizaciones...")
        menu_archivo.Append(id_cerrar_sesion, "&Cerrar sesión...")
        menu_archivo.Append(wx.ID_EXIT, "&Salir\tCtrl+Q")
        barra.Append(menu_archivo, "&Archivo")
        self.SetMenuBar(barra)

        self.Bind(wx.EVT_MENU, self._al_anadir, id=id_anadir)
        self.Bind(wx.EVT_MENU, lambda e: self.sincronizar_en_segundo_plano(), id=id_sincronizar)
        self.Bind(wx.EVT_MENU, self._al_buscar_actualizaciones, id=id_actualizar)
        self.Bind(wx.EVT_MENU, self._al_cerrar_sesion, id=id_cerrar_sesion)
        self.Bind(wx.EVT_MENU, lambda e: self.Close(), id=wx.ID_EXIT)

    def _construir_controles(self) -> None:
        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        fila_filtros = wx.BoxSizer(wx.HORIZONTAL)

        # Cada etiqueta se crea justo antes de su control: es lo que le da el
        # nombre accesible (ver ui/campos.py). Un TextCtrl y no un SearchCtrl: el
        # SearchCtrl mete el campo dentro de otra ventana, el foco va a ese campo
        # interior y la etiqueta ya no le queda delante.
        etiqueta_buscar, self.buscador = con_etiqueta(panel, "&Buscar:", wx.TextCtrl)
        self.buscador.SetHint("Título, URL o etiqueta")
        self.buscador.Bind(wx.EVT_TEXT, self._al_cambiar_filtro)
        fila_filtros.Add(etiqueta_buscar, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 4)
        fila_filtros.Add(self.buscador, 1, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 12)

        etiqueta_filtro, self.selector_etiqueta = con_etiqueta(
            panel, "&Etiqueta:", lambda padre: wx.Choice(padre, choices=[_TODAS_LAS_ETIQUETAS])
        )
        self.selector_etiqueta.SetSelection(0)
        self.selector_etiqueta.Bind(wx.EVT_CHOICE, self._al_cambiar_filtro)
        fila_filtros.Add(etiqueta_filtro, 0, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 4)
        fila_filtros.Add(self.selector_etiqueta, 0, wx.ALIGN_CENTER_VERTICAL)

        sizer.Add(fila_filtros, 0, wx.EXPAND | wx.ALL, 8)

        # Sin cabecera: con una sola columna no dice nada que no diga la etiqueta.
        etiqueta_lista, self.lista = con_etiqueta(
            panel,
            "En&laces:",
            lambda padre: wx.ListCtrl(padre, style=wx.LC_REPORT | wx.LC_SINGLE_SEL | wx.LC_NO_HEADER),
        )
        self.lista.InsertColumn(0, "Enlace guardado", width=720)
        self.lista.Bind(wx.EVT_LIST_ITEM_ACTIVATED, self._al_abrir_seleccionado)
        self.lista.Bind(wx.EVT_CONTEXT_MENU, self._al_menu_contextual)
        sizer.Add(etiqueta_lista, 0, wx.LEFT | wx.RIGHT, 8)
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

    def enfocar_lo_primero(self) -> None:
        """El foco a la lista, que es a lo que se viene. Hay que llamarlo con la
        ventana ya activa (main.py lo hace con wx.CallAfter): antes, Windows no
        lo respeta y el foco cae en el buscador, el primer control."""
        self.lista.SetFocus()

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
        n = len(elementos)
        # el StatusBar de Win32 se anuncia solo al cambiar (no hace falta anuncio aparte)
        self.SetStatusText(f"{n} elemento{'s' if n != 1 else ''}")

        filas = [(elemento.id, texto_fila(elemento)) for elemento in elementos]
        if filas == self._filas:
            # Esto se llama en cada sincronizacion, tambien mientras se esta
            # leyendo la lista: rehacerla sin cambios movia al lector de sitio.
            return
        destino = fila_tras_refrescar(
            [id_ for id_, _ in self._filas],
            self.lista.GetFirstSelected(),
            [id_ for id_, _ in filas],
        )
        self._filas = filas

        self.lista.Freeze()
        try:
            self.lista.DeleteAllItems()
            for indice, (_, texto) in enumerate(filas):
                self.lista.InsertItem(indice, texto)
        finally:
            self.lista.Thaw()

        if destino >= 0:
            # Focus marca la fila activa, la que lee el lector al entrar en la
            # lista. No es el foco del teclado: quien este escribiendo en el
            # buscador sigue alli.
            self.lista.Select(destino)
            self.lista.Focus(destino)

    def _elemento_en(self, indice: int) -> Elemento | None:
        if 0 <= indice < len(self._elementos_mostrados):
            return self._elementos_mostrados[indice]
        return None

    def _al_cambiar_filtro(self, evento: wx.Event) -> None:
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
        item_editar = menu.Append(wx.ID_ANY, "Editar e&tiquetas...")
        menu.AppendSeparator()
        item_eliminar = menu.Append(wx.ID_ANY, "&Eliminar")

        self.Bind(wx.EVT_MENU, lambda e: webbrowser.open(elemento.url), item_abrir)
        self.Bind(wx.EVT_MENU, lambda e: self._copiar_url(elemento), item_copiar)
        self.Bind(wx.EVT_MENU, lambda e: self._mostrar_detalle(elemento), item_editar)
        self.Bind(wx.EVT_MENU, lambda e: self._confirmar_y_eliminar(elemento), item_eliminar)

        self.PopupMenu(menu)
        menu.Destroy()

    def _copiar_url(self, elemento: Elemento) -> None:
        if not wx.TheClipboard.Open():
            # Otro programa puede tenerlo abierto; decir "copiada" seria mentir.
            self.SetStatusText("No se pudo copiar la URL")
            return
        wx.TheClipboard.SetData(wx.TextDataObject(elemento.url))
        wx.TheClipboard.Close()
        self.SetStatusText(f"URL copiada: {elemento.url}")

    def _al_eliminar_seleccionado(self, evento: wx.CommandEvent) -> None:
        elemento = self._elemento_en(self.lista.GetFirstSelected())
        if elemento:
            self._confirmar_y_eliminar(elemento)

    def _confirmar_y_eliminar(self, elemento: Elemento) -> None:
        if confirmar_eliminacion(elemento.titulo or elemento.url, self):
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

    def _al_activar(self, evento: wx.ActivateEvent) -> None:
        """Volver a la ventana trae lo que se haya guardado en el movil mientras
        tanto. Sin esto solo se enteraba con F5, y "lo guarde en el otro sitio y
        aqui no esta" es de las cosas que mas desconfianza dan."""
        evento.Skip()
        if evento.GetActive() and toca_sincronizar(
            self._ultima_sincronizacion, time.monotonic()
        ):
            self.sincronizar_en_segundo_plano()

    def sincronizar_en_segundo_plano(self) -> None:
        self._ultima_sincronizacion = time.monotonic()

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

    # --- actualizaciones ---

    def _al_buscar_actualizaciones(self, evento: wx.CommandEvent) -> None:
        self._buscar_actualizaciones_en_segundo_plano(manual=True)

    def _buscar_actualizaciones_en_segundo_plano(self, manual: bool) -> None:
        """`manual` distingue quien pregunta. Al arrancar se calla si no hay
        nada; pedido desde el menu hay que contestar siempre, aunque sea para
        decir que ya esta al dia: un menu que no responde parece roto."""
        if not actualizaciones.esta_empaquetada():
            if manual:
                avisar(
                    "Esto se actualiza con git, no desde aquí: la aplicación no "
                    "está corriendo como ejecutable.",
                    "Buscar actualizaciones",
                    self,
                )
            return

        def trabajo() -> None:
            disponible = actualizaciones.comprobar()
            wx.CallAfter(self._al_terminar_comprobacion, disponible, manual)

        threading.Thread(target=trabajo, daemon=True).start()

    def _al_terminar_comprobacion(self, disponible, manual: bool) -> None:
        if disponible is None:
            if manual:
                avisar(f"Ya tienes la última versión ({VERSION}).", "Buscar actualizaciones", self)
            return

        novedades = f" {disponible.novedades}" if disponible.novedades else ""
        dialogo = wx.MessageDialog(
            self,
            f"Hay una versión nueva ({disponible.version}); tú tienes la "
            f"{VERSION}.{novedades} Si la instalas, la aplicación se cerrará y "
            "volverá a abrirse sola.",
            "Nueva versión disponible",
            # Aceptar y Cancelar y no Si y No: sin boton de cancelar, Escape no
            # cierra el cuadro.
            wx.OK | wx.CANCEL | wx.ICON_QUESTION,
        )
        dialogo.SetOKCancelLabels("&Instalar ahora", "Ahora no")
        instalar = dialogo.ShowModal() == wx.ID_OK
        dialogo.Destroy()
        if instalar:
            self._instalar(disponible)

    def _instalar(self, disponible) -> None:
        self.SetStatusText(f"Descargando la versión {disponible.version}…")

        def trabajo() -> None:
            destino = Path(tempfile.gettempdir()) / f"GuardarEnlaces-{disponible.version}.zip"
            if not actualizaciones.descargar(disponible, destino):
                wx.CallAfter(self._fallo_actualizando, "La descarga falló o llegó corrompida.")
                return
            carpeta = actualizaciones.descomprimir(destino)
            if carpeta is None:
                wx.CallAfter(self._fallo_actualizando, "El paquete descargado no es válido.")
                return
            wx.CallAfter(self._relevar, carpeta)

        threading.Thread(target=trabajo, daemon=True).start()

    def _relevar(self, carpeta_nueva: Path) -> None:
        """Lanza el relevo y cierra: el .cmd esta esperando a que este proceso
        muera para poder sustituir el ejecutable."""
        registro = actualizaciones.aplicar(carpeta_nueva)
        # Si la aplicacion no vuelve a abrirse, ese fichero dice por donde fallo.
        # Es lo unico que queda para diagnosticarlo, porque a partir de aqui ya
        # no hay ninguna ventana donde contar nada.
        logging.info("relevo lanzado, registro en %s", registro)
        self.Close()

    def _fallo_actualizando(self, mensaje: str) -> None:
        # No se toca nada de la instalacion hasta tener el paquete entero y
        # verificado, asi que un fallo aqui deja la aplicacion como estaba.
        self.SetStatusText("")
        avisar(
            f"{mensaje} La aplicación sigue funcionando; puedes intentarlo más tarde.",
            "No se pudo actualizar",
            self,
            grave=True,
        )

    # --- cuenta ---

    def _al_cerrar_sesion(self, evento: wx.CommandEvent) -> None:
        """Cerrar sesion y ofrecer entrar con otra cuenta sin reiniciar.

        Los enlaces NO se borran: se quedan aqui y, si luego entra otra cuenta,
        asentar_cuenta pregunta antes de mezclarlos (y si vuelve la misma, no
        pregunta nada). Por eso el aviso lo dice: lo que se pierde es la sesion,
        no los datos.
        """
        confirmar = wx.MessageDialog(
            self,
            f"Se cerrará la sesión de {(self._sesion.usuario or {}).get('email')}. "
            "Tus enlaces se quedan en este equipo. Para volver a sincronizar "
            "tendrás que entrar de nuevo con Google.",
            "Cerrar sesión",
            wx.OK | wx.CANCEL | wx.ICON_QUESTION,
        )
        confirmar.SetOKCancelLabels("Cerrar &sesión", "&Cancelar")
        salir = confirmar.ShowModal() == wx.ID_OK
        confirmar.Destroy()
        if not salir:
            return

        self.SetStatusText("Cerrando sesión…")

        def trabajo() -> None:
            # cerrar() avisa al servidor para revocar el token; si no hay red se
            # cierra igual en local (lo resuelve Sesion, no hace falta nada aqui).
            self._sesion.cerrar()
            wx.CallAfter(self._tras_cerrar_sesion)

        threading.Thread(target=trabajo, daemon=True).start()

    def _tras_cerrar_sesion(self) -> None:
        """Sin cuenta esta aplicacion no tiene nada que hacer --existe para
        sincronizar--, asi que o entra alguien o se cierra."""
        self.SetTitle(_titulo_con_cuenta(self._sesion))
        self.SetStatusText("Sesión cerrada")

        dialogo = DialogoLogin(self, self._sesion, self._cliente)
        entro = dialogo.ShowModal() == wx.ID_OK
        dialogo.Destroy()
        if not entro:
            self.Close()
            return

        correo = (self._sesion.usuario or {}).get("email")
        if correo:
            asentar_cuenta(
                self._almacen,
                identidad_dueno(self._cliente.url_base, correo),
                lambda enlaces: preguntar_importacion(enlaces, self),
            )
        self.SetTitle(_titulo_con_cuenta(self._sesion))
        self._cargar_desde_cache()
        self._ultima_sincronizacion = 0.0  # cuenta nueva: sincronizar ya, sin esperar al freno
        self.sincronizar_en_segundo_plano()

    # --- cierre ---

    def _al_cerrar(self, evento: wx.CloseEvent) -> None:
        self._icono_bandeja.Destroy()
        self.Destroy()
