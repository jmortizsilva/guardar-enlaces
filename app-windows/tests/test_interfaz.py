"""Lo que se puede comprobar de la interfaz sin lector de pantalla.

No sustituye a probar con NVDA. Para el nombre de los campos mira lo mismo que
usa Windows: el wx.StaticText creado justo antes que cada uno (ver
docs/ACCESIBILIDAD-WXPYTHON.md). Antes se comprobaba GetName(), y pasaba en
verde con el buscador y la lista anunciandose sin nombre."""

from __future__ import annotations

import re
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import wx

from guardar_enlaces.almacen_local import AlmacenLocal
from guardar_enlaces.api_cliente import ErrorApi
from guardar_enlaces.modelo import nuevo_elemento_local
from guardar_enlaces.ui import dialogo_detalle
from guardar_enlaces.ui.bandeja import IconoBandeja
from guardar_enlaces.ui.campos import etiqueta_de, etiqueta_widget_de
from guardar_enlaces.ui.dialogo_anadir import DialogoAnadir
from guardar_enlaces.ui.dialogo_detalle import DialogoDetalle
from guardar_enlaces.ui.dialogo_login import DialogoLogin
from guardar_enlaces.ui.ventana_principal import VentanaPrincipal
from guardar_enlaces.voz import SinScreenReader, Voz

# Los controles que toman el nombre de la etiqueta de delante. Los botones no:
# su propio texto es el nombre.
TOMAN_NOMBRE_DE_ETIQUETA = (wx.TextCtrl, wx.Choice, wx.ComboBox, wx.ListCtrl)


@pytest.fixture(scope="module")
def app():
    return wx.App()


@pytest.fixture
def almacen():
    a = AlmacenLocal(":memory:")
    yield a
    a.cerrar()


def _ventana(almacen: AlmacenLocal, voz: Voz | None = None) -> VentanaPrincipal:
    # El constructor lanza una sincronizacion en segundo plano; con una sesion mock que no
    # simula una respuesta real, que falle limpio con ErrorApi (evita ruido de una excepcion
    # sin capturar en el hilo de fondo, que no tiene nada que ver con estas pruebas).
    sesion = MagicMock()
    sesion.con_reintento.side_effect = ErrorApi("sin red en el test")
    # Sin el lector de verdad: las pruebas no pueden ponerse a hablar por NVDA.
    return VentanaPrincipal(almacen, MagicMock(), sesion, voz if voz is not None else Voz(SinScreenReader()))


class _ScreenReaderQueApunta:
    def __init__(self):
        self.dicho = []

    def decir(self, texto):
        self.dicho.append(texto)


def test_lo_que_se_escribe_en_la_barra_tambien_se_dice_medio_segundo_despues(
    app, almacen, monkeypatch
):
    programados = []
    monkeypatch.setattr(
        wx, "CallLater", lambda ms, funcion, *args: programados.append((ms, funcion, args))
    )
    elemento = nuevo_elemento_local("https://a.com", titulo="A")
    almacen.marcar_pendiente(elemento)
    screen_reader = _ScreenReaderQueApunta()
    ventana = _ventana(almacen, Voz(screen_reader))
    try:
        ventana._al_elemento_eliminado(elemento)
        assert ventana.GetStatusBar().GetStatusText() == "Eliminado: A"
        # Todavia no: al cerrarse el cuadro, la relectura de la lista lo pisaria.
        assert screen_reader.dicho == []
        assert [ms for ms, _, _ in programados] == [500]

        _, funcion, args = programados[0]
        funcion(*args)
        assert screen_reader.dicho == ["Eliminado: A"]
    finally:
        ventana.Destroy()


def test_lo_que_no_viene_de_cerrar_una_ventana_se_dice_sin_esperar(app, almacen, monkeypatch):
    # F5 no cierra nada: esperar medio segundo solo retrasaria la respuesta.
    programados = []
    monkeypatch.setattr(wx, "CallLater", lambda *args: programados.append(args))
    screen_reader = _ScreenReaderQueApunta()
    ventana = _ventana(almacen, Voz(screen_reader))
    try:
        ventana._decir_estado("Sincronizado")
        assert screen_reader.dicho == ["Sincronizado"]
        assert programados == []
    finally:
        ventana.Destroy()


def _descendientes(ventana: wx.Window):
    for hijo in ventana.GetChildren():
        yield hijo
        yield from _descendientes(hijo)


def _sin_etiqueta(ventana: wx.Window) -> list[str]:
    return [
        type(control).__name__
        for control in _descendientes(ventana)
        if isinstance(control, TOMAN_NOMBRE_DE_ETIQUETA) and not etiqueta_de(control)
    ]


def _solo_lectura_de_una_linea(ventana: wx.Window) -> list[wx.TextCtrl]:
    # De una sola linea, el tabulador no llega a ellos.
    return [
        control
        for control in _descendientes(ventana)
        if isinstance(control, wx.TextCtrl) and not control.IsEditable() and not control.IsMultiLine()
    ]


def _teclas_de_acceso(etiquetas: list[str]) -> list[str]:
    # Lo que va detras del tabulador en un menu es el atajo, no la etiqueta.
    return [
        marca.group(1).lower()
        for etiqueta in etiquetas
        for marca in re.finditer(r"&([^&])", etiqueta.split("\t")[0])
    ]


def _teclas_de_la_pantalla(ventana: wx.Window) -> list[str]:
    etiquetas = [
        control.GetLabel()
        for control in _descendientes(ventana)
        if isinstance(control, (wx.StaticText, wx.Button))
    ]
    barra = ventana.GetMenuBar() if isinstance(ventana, wx.Frame) else None
    if barra:
        etiquetas += [barra.GetMenuLabel(i) for i in range(barra.GetMenuCount())]
    return _teclas_de_acceso(etiquetas)


def _comprobar_teclas(teclas: list[str]) -> None:
    # Con dos controles en la misma letra, Windows alterna entre ellos.
    repetidas = sorted({tecla for tecla in teclas if teclas.count(tecla) > 1})
    assert not repetidas, f"teclas de acceso repetidas: {repetidas}"
    assert all(tecla.isascii() for tecla in teclas), f"tecla de acceso con tilde: {teclas}"


def _comprobar_pantalla(ventana: wx.Window) -> None:
    assert _sin_etiqueta(ventana) == []
    assert _solo_lectura_de_una_linea(ventana) == []
    _comprobar_teclas(_teclas_de_la_pantalla(ventana))


def test_ningun_cuadro_de_wx_sale_con_los_botones_en_ingles():
    # wx.MessageBox pone "OK" y no deja cambiarlo; los avisos van por
    # preguntas.avisar, que dice Aceptar.
    carpeta = Path(__file__).resolve().parent.parent / "guardar_enlaces"
    colados = [
        f"{fichero.name}:{numero}"
        for fichero in carpeta.rglob("*.py")
        for numero, linea in enumerate(fichero.read_text(encoding="utf-8").splitlines(), 1)
        if "wx.MessageBox(" in linea.split("#")[0]
    ]
    assert colados == []


def test_la_comprobacion_caza_un_campo_metido_dentro_de_otro_control(app):
    # Era el buscador: la etiqueta iba delante del SearchCtrl, pero el foco va al
    # campo que el SearchCtrl lleva dentro, y ese no tiene nada delante.
    marco = wx.Frame(None)
    try:
        panel = wx.Panel(marco)
        wx.StaticText(panel, label="&Buscar:")
        wx.SearchCtrl(panel)
        assert _sin_etiqueta(marco) == ["TextCtrl"]
    finally:
        marco.Destroy()


def test_ventana_principal_cada_campo_tiene_su_etiqueta(app, almacen):
    ventana = _ventana(almacen)
    try:
        assert etiqueta_de(ventana.buscador) == "Buscar:"
        assert etiqueta_de(ventana.selector_etiqueta) == "Etiqueta:"
        assert etiqueta_de(ventana.lista) == "Enlaces:"
        _comprobar_pantalla(ventana)
    finally:
        ventana.Destroy()


def test_los_menus_no_repiten_tecla_de_acceso(app, almacen):
    ventana = _ventana(almacen)
    try:
        barra = ventana.GetMenuBar()
        for i in range(barra.GetMenuCount()):
            _comprobar_teclas(
                _teclas_de_acceso([item.GetItemLabel() for item in barra.GetMenu(i).GetMenuItems()])
            )
    finally:
        ventana.Destroy()

    bandeja = IconoBandeja(MagicMock())
    try:
        menu = bandeja.CreatePopupMenu()
        _comprobar_teclas(_teclas_de_acceso([item.GetItemLabel() for item in menu.GetMenuItems()]))
        menu.Destroy()
    finally:
        bandeja.Destroy()


def test_la_lista_tiene_siempre_una_fila_activa_y_sigue_al_mismo_enlace(app, almacen):
    for url in ("https://a.com", "https://b.com"):
        almacen.marcar_pendiente(nuevo_elemento_local(url, titulo=url))
    ventana = _ventana(almacen)
    try:
        lista = ventana.lista
        # Sin fila activa, al entrar en la lista el lector dice su nombre y nada mas.
        assert lista.GetFirstSelected() == 0
        assert lista.GetFocusedItem() == 0

        lista.Select(1)
        lista.Focus(1)
        elegido = ventana._elementos_mostrados[1].id
        almacen.marcar_pendiente(nuevo_elemento_local("https://c.com", titulo="c"))
        ventana._cargar_desde_cache()
        fila = lista.GetFirstSelected()
        assert ventana._elementos_mostrados[fila].id == elegido
        assert lista.GetFocusedItem() == fila

        rehechas = []
        borrar_todo = lista.DeleteAllItems
        lista.DeleteAllItems = lambda: rehechas.append(1) or borrar_todo()
        ventana._cargar_desde_cache()  # lo que hace cada sincronizacion sin novedades
        assert rehechas == []
    finally:
        ventana.Destroy()


def test_dialogo_login_se_puede_leer_y_el_estado_no_esta_hasta_que_dice_algo(app):
    dialogo = DialogoLogin(None, MagicMock(), MagicMock())
    try:
        assert etiqueta_de(dialogo.aviso) == "Qué hay que hacer:"
        assert etiqueta_de(dialogo.estado) == "Estado:"
        _comprobar_pantalla(dialogo)

        assert not dialogo.estado.IsShown()
        assert not etiqueta_widget_de(dialogo.estado).IsShown()
        dialogo._decir("No se pudo abrir el navegador.")
        assert dialogo.estado.IsShown()
        assert etiqueta_widget_de(dialogo.estado).IsShown()
        assert dialogo.estado.GetValue() == "No se pudo abrir el navegador."
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_se_puede_leer_el_fallo_de_la_comprobacion(app):
    dialogo = DialogoAnadir(None, MagicMock(), MagicMock())
    try:
        assert etiqueta_de(dialogo.campo_url) == "URL:"
        assert etiqueta_de(dialogo.vista_previa) == "Vista previa:"
        _comprobar_pantalla(dialogo)

        assert not dialogo.vista_previa.IsShown()
        dialogo._al_fallar_comprobacion("sin red")
        assert dialogo.vista_previa.IsShown()
        assert dialogo.vista_previa.GetValue() == "No se pudo comprobar: sin red"
    finally:
        dialogo.Destroy()


def test_dialogo_detalle_se_pueden_leer_los_datos_del_enlace(app):
    elemento = nuevo_elemento_local("https://a.com", titulo="A")
    dialogo = DialogoDetalle(None, elemento, lambda e: None, lambda e: None)
    try:
        assert etiqueta_de(dialogo.campo_enlace) == "Enlace:"
        assert etiqueta_de(dialogo.campo_etiquetas) == "Etiquetas, separadas por comas:"
        assert dialogo.campo_enlace.GetValue() == "A\nhttps://a.com"
        _comprobar_pantalla(dialogo)
    finally:
        dialogo.Destroy()


def _pulsar(boton: wx.Button) -> None:
    evento = wx.CommandEvent(wx.wxEVT_BUTTON, boton.GetId())
    evento.SetEventObject(boton)
    boton.GetEventHandler().ProcessEvent(evento)


@pytest.mark.parametrize("confirma", [True, False])
def test_dialogo_detalle_eliminar_pregunta_y_avisa_con_el_elemento(app, monkeypatch, confirma):
    # El aviso de borrado se guardaba con el mismo nombre que el metodo del boton
    # y lo tapaba: se borraba sin preguntar y pasando el evento de wx.
    preguntas = []
    monkeypatch.setattr(
        dialogo_detalle,
        "confirmar_eliminacion",
        lambda titulo, padre: preguntas.append(titulo) or confirma,
    )
    elemento = nuevo_elemento_local("https://a.com", titulo="A")
    eliminados = []
    dialogo = DialogoDetalle(None, elemento, lambda e: None, eliminados.append)
    dialogo.EndModal = lambda codigo: None  # no esta abierto de verdad
    try:
        _pulsar(dialogo.boton_eliminar)
        assert preguntas == ["A"]
        assert eliminados == ([elemento] if confirma else [])
    finally:
        dialogo.Destroy()
