"""Lo que se puede comprobar de la interfaz sin lector de pantalla.

No sustituye a probar con NVDA. Para el nombre de los campos mira lo mismo que
usa Windows: el wx.StaticText creado justo antes que cada uno (ver
docs/ACCESIBILIDAD-WXPYTHON.md). Antes se comprobaba GetName(), y pasaba en
verde con el buscador y la lista anunciandose sin nombre."""

from __future__ import annotations

import re
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import wx

from guardar_enlaces.almacen_local import AlmacenLocal
from guardar_enlaces.api_cliente import ErrorApi
from guardar_enlaces.modelo import elementos_visibles, nueva_etiqueta_definida, nuevo_elemento_local
from guardar_enlaces.ui import dialogo_anadir, dialogo_detalle, dialogo_gestion_etiquetas, ventana_principal
from guardar_enlaces.ui.bandeja import IconoBandeja
from guardar_enlaces.ui.campos import etiqueta_de, etiqueta_widget_de
from guardar_enlaces.ui.dialogo_anadir import DialogoAnadir
from guardar_enlaces.ui.dialogo_detalle import DialogoDetalle
from guardar_enlaces.ui.dialogo_gestion_etiquetas import DialogoGestionEtiquetas
from guardar_enlaces.ui.dialogo_login import DialogoLogin
from guardar_enlaces import login_oauth
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


def test_dialogo_login_ofrece_las_dos_cuentas(app):
    cliente = MagicMock()
    dialogo = DialogoLogin(None, MagicMock(), cliente)
    try:
        assert dialogo.boton_entrar.GetLabel() == "&Entrar con Google"
        assert dialogo.boton_entrar_apple.GetLabel() == "Entrar con A&pple"
        # Cada boton pide SU proveedor: con los dos pidiendo "google" el de
        # Apple parecia funcionar y entraba por el otro lado.
        with patch.object(login_oauth, "abrir_navegador", return_value=False):
            dialogo._al_entrar("apple")
        assert cliente.url_iniciar_login.call_args.args[0] == "apple"

        with patch.object(login_oauth, "abrir_navegador", return_value=False):
            dialogo._al_entrar("google")
        assert cliente.url_iniciar_login.call_args.args[0] == "google"
    finally:
        dialogo.Destroy()


def test_dialogo_login_avisa_de_que_google_y_apple_son_cuentas_distintas(app):
    dialogo = DialogoLogin(None, MagicMock(), MagicMock())
    try:
        # Entrar con la otra y encontrarse la biblioteca vacia asusta; decirlo
        # antes cuesta una linea.
        assert "cuentas distintas" in dialogo.aviso.GetValue()
    finally:
        dialogo.Destroy()


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


def test_dialogo_anadir_los_campos_se_pueden_leer(app):
    dialogo = DialogoAnadir(None, MagicMock(), MagicMock())
    try:
        assert etiqueta_de(dialogo.campo_url) == "URL:"
        assert etiqueta_de(dialogo.campo_etiquetas) == "Etiquetas, separadas por comas:"
        assert etiqueta_de(dialogo.estado) == "Estado:"
        assert not dialogo.estado.IsShown()
        _comprobar_pantalla(dialogo)
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_url_invalida_no_llega_a_la_red(app):
    cliente = MagicMock()
    dialogo = DialogoAnadir(None, cliente, MagicMock())
    dialogo.EndModal = lambda codigo: None  # no esta abierto de verdad
    try:
        dialogo.campo_url.SetValue("no-es-una-url")
        _pulsar(dialogo.boton_guardar)

        assert dialogo.estado.GetValue() == "Escribe una URL que empiece por http:// o https://"
        assert dialogo.elemento_creado is None
        cliente.metadatos.assert_not_called()
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_un_duplicado_cancelado_no_llega_a_la_red(app, monkeypatch):
    preguntas = []
    monkeypatch.setattr(
        dialogo_anadir,
        "confirmar_guardar_duplicado",
        lambda titulo, padre: preguntas.append(titulo) or False,
    )
    existente = nuevo_elemento_local("https://a.com", titulo="Ya guardado")
    cliente = MagicMock()
    dialogo = DialogoAnadir(None, cliente, MagicMock(), guardados=[existente])
    dialogo.EndModal = lambda codigo: None
    try:
        dialogo.campo_url.SetValue("https://a.com")
        _pulsar(dialogo.boton_guardar)

        assert preguntas == ["Ya guardado"]
        assert dialogo.elemento_creado is None
        cliente.metadatos.assert_not_called()
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_completar_guardado_crea_el_elemento_con_metadatos_y_etiquetas(app):
    dialogo = DialogoAnadir(None, MagicMock(), MagicMock())
    dialogo.EndModal = lambda codigo: None
    try:
        dialogo._al_completar_guardado(
            "https://a.com",
            {"titulo": "A", "descripcion": "d", "imagenUrl": "https://a.com/i.jpg", "tipo": "articulo"},
            ("ocio", "trabajo"),
            None,
        )
        e = dialogo.elemento_creado
        assert e.url == "https://a.com"
        assert e.titulo == "A"
        assert e.descripcion == "d"
        assert e.imagen_url == "https://a.com/i.jpg"
        assert e.tipo == "articulo"
        assert e.etiquetas == ("ocio", "trabajo")
        assert dialogo.actualizado_existente is False
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_completar_guardado_sin_metadatos_guarda_solo_la_url(app):
    # La comprobacion fallo (sin red, sitio caido): se guarda igual.
    dialogo = DialogoAnadir(None, MagicMock(), MagicMock())
    dialogo.EndModal = lambda codigo: None
    try:
        dialogo._al_completar_guardado("https://a.com", {}, (), None)
        e = dialogo.elemento_creado
        assert e.url == "https://a.com"
        assert e.titulo is None
        assert e.tipo == "enlace"
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_completar_guardado_con_duplicado_actualiza_el_existente(app):
    existente = nuevo_elemento_local(
        "https://a.com", titulo="Viejo", etiquetas=("archivado",), ahora=lambda: 100
    )
    dialogo = DialogoAnadir(None, MagicMock(), MagicMock())
    dialogo.EndModal = lambda codigo: None
    try:
        dialogo._al_completar_guardado(
            "https://a.com", {"titulo": "Nuevo"}, ("reciente",), existente
        )
        e = dialogo.elemento_creado
        assert e.id == existente.id  # mismo enlace, no uno nuevo
        assert e.titulo == "Nuevo"
        assert e.etiquetas == ("archivado", "reciente")  # fusionadas, ninguna se pierde
        assert dialogo.actualizado_existente is True
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_completar_guardado_con_duplicado_y_sin_metadatos_conserva_lo_que_habia(app):
    existente = nuevo_elemento_local("https://a.com", titulo="Viejo", descripcion="antes")
    dialogo = DialogoAnadir(None, MagicMock(), MagicMock())
    dialogo.EndModal = lambda codigo: None
    try:
        dialogo._al_completar_guardado("https://a.com", {}, (), existente)
        e = dialogo.elemento_creado
        assert e.titulo == "Viejo"
        assert e.descripcion == "antes"
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_cancelar_mientras_se_guarda_no_completa_el_guardado(app):
    dialogo = DialogoAnadir(None, MagicMock(), MagicMock())
    dialogo.EndModal = lambda codigo: None
    try:
        dialogo._cerrado = True  # como si se hubiera pulsado Cancelar mientras se comprobaba
        dialogo._al_completar_guardado("https://a.com", {"titulo": "A"}, (), None)
        assert dialogo.elemento_creado is None
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


def _tecla(control: wx.Window, codigo: int) -> None:
    evento = wx.KeyEvent(wx.wxEVT_KEY_DOWN)
    evento.SetKeyCode(codigo)
    evento.SetEventObject(control)
    control.GetEventHandler().ProcessEvent(evento)


def test_suprimir_solo_actua_si_el_foco_esta_en_la_lista(app, almacen, monkeypatch):
    # Antes era un AcceleratorTable en toda la ventana: Suprimir borraba el
    # elemento activo de la lista aunque el foco estuviera en el buscador o en
    # el filtro de etiquetas, donde Suprimir tiene su propio significado.
    almacen.marcar_pendiente(nuevo_elemento_local("https://a.com", titulo="A"))
    preguntas = []
    monkeypatch.setattr(
        ventana_principal, "confirmar_eliminacion", lambda titulo, padre: preguntas.append(titulo) or True
    )
    ventana = _ventana(almacen)
    try:
        assert not ventana.GetAcceleratorTable().IsOk()  # nada de Suprimir a nivel de ventana

        ventana.lista.Select(0)
        ventana.lista.Focus(0)
        _tecla(ventana.selector_etiqueta, wx.WXK_DELETE)
        assert preguntas == []  # el foco no estaba en la lista: no pregunta nada

        _tecla(ventana.lista, wx.WXK_DELETE)
        assert preguntas == ["A"]
    finally:
        ventana.Destroy()


def _dialogo_gestion(
    elementos: list, reservadas: list | None = None
) -> tuple[DialogoGestionEtiquetas, list, list, list]:
    renombrados: list[tuple[str, str]] = []
    eliminados: list[str] = []
    anadidos: list[str] = []
    dialogo = DialogoGestionEtiquetas(
        None,
        obtener_elementos=lambda: elementos,
        obtener_reservadas=lambda: reservadas or [],
        al_renombrar=lambda vieja, nueva: renombrados.append((vieja, nueva)),
        al_eliminar=eliminados.append,
        al_anadir=anadidos.append,
    )
    return dialogo, renombrados, eliminados, anadidos


def test_dialogo_gestion_etiquetas_muestra_nombre_y_recuento(app):
    elementos = [
        nuevo_elemento_local("https://a.com", etiquetas=("ocio", "trabajo")),
        nuevo_elemento_local("https://b.com", etiquetas=("ocio",)),
    ]
    dialogo, _, _, _ = _dialogo_gestion(elementos)
    try:
        assert etiqueta_de(dialogo.lista) == "Etiquetas:"
        filas = [dialogo.lista.GetItemText(i) for i in range(dialogo.lista.GetItemCount())]
        assert filas == ["ocio (2 enlaces)", "trabajo (1 enlace)"]
        _comprobar_pantalla(dialogo)
    finally:
        dialogo.Destroy()


def test_dialogo_gestion_etiquetas_incluye_las_reservadas_sin_ningun_enlace(app):
    reservada = nueva_etiqueta_definida("vacaciones")
    dialogo, _, _, _ = _dialogo_gestion([], reservadas=[reservada])
    try:
        filas = [dialogo.lista.GetItemText(i) for i in range(dialogo.lista.GetItemCount())]
        assert filas == ["vacaciones (0 enlaces)"]
    finally:
        dialogo.Destroy()


def test_dialogo_gestion_etiquetas_suprimir_solo_actua_en_la_lista(app, monkeypatch):
    monkeypatch.setattr(dialogo_gestion_etiquetas, "confirmar_eliminar_etiqueta", lambda *a: True)
    elementos = [nuevo_elemento_local("https://a.com", etiquetas=("ocio",))]
    dialogo, _, eliminados, _ = _dialogo_gestion(elementos)
    try:
        dialogo.lista.Select(0)
        dialogo.lista.Focus(0)
        _tecla(dialogo.boton_cerrar, wx.WXK_DELETE)  # cualquier otro control del dialogo
        assert eliminados == []

        _tecla(dialogo.lista, wx.WXK_DELETE)
        assert eliminados == ["ocio"]
    finally:
        dialogo.Destroy()


def test_dialogo_gestion_etiquetas_eliminar_pregunta_y_refresca(app, monkeypatch):
    preguntas = []
    monkeypatch.setattr(
        dialogo_gestion_etiquetas,
        "confirmar_eliminar_etiqueta",
        lambda etiqueta, n, padre: preguntas.append((etiqueta, n)) or False,
    )
    elementos = [nuevo_elemento_local("https://a.com", etiquetas=("ocio",))]
    dialogo, _, eliminados, _ = _dialogo_gestion(elementos)
    try:
        dialogo._eliminar("ocio")
        assert preguntas == [("ocio", 1)]
        assert eliminados == []  # cancelado: no se llama al callback
    finally:
        dialogo.Destroy()


def _dialogo_nombre_falso(nombre: str):
    class _DialogoFalso:
        def __init__(self, *args, **kwargs):
            self.nombre = nombre

        def ShowModal(self):
            return wx.ID_OK

        def Destroy(self):
            pass

    return _DialogoFalso


def test_dialogo_gestion_etiquetas_renombrar_llama_al_callback_y_refresca(app, monkeypatch):
    monkeypatch.setattr(
        dialogo_gestion_etiquetas, "DialogoNombreEtiqueta", _dialogo_nombre_falso("hobby")
    )
    elementos = [nuevo_elemento_local("https://a.com", etiquetas=("ocio",))]
    dialogo, renombrados, _, _ = _dialogo_gestion(elementos)
    try:
        dialogo._renombrar("ocio")
        assert renombrados == [("ocio", "hobby")]
    finally:
        dialogo.Destroy()


def test_dialogo_gestion_etiquetas_anadir_llama_al_callback_y_refresca(app, monkeypatch):
    monkeypatch.setattr(
        dialogo_gestion_etiquetas, "DialogoNombreEtiqueta", _dialogo_nombre_falso("vacaciones")
    )
    dialogo, _, _, anadidos = _dialogo_gestion([])
    try:
        dialogo._anadir()
        assert anadidos == ["vacaciones"]
    finally:
        dialogo.Destroy()


def test_dialogo_nombre_etiqueta_se_puede_leer_y_valida_el_nombre(app, monkeypatch):
    avisos = []
    monkeypatch.setattr(
        dialogo_gestion_etiquetas, "avisar", lambda texto, titulo, padre: avisos.append(texto)
    )
    dialogo = dialogo_gestion_etiquetas.DialogoNombreEtiqueta(
        None, "Renombrar «ocio»", "&Guardar", valor_inicial="ocio"
    )
    dialogo.EndModal = lambda codigo: None  # no esta abierto de verdad
    try:
        assert etiqueta_de(dialogo.campo_nombre) == "Nombre:"
        assert dialogo.campo_nombre.GetValue() == "ocio"
        _comprobar_pantalla(dialogo)

        dialogo.campo_nombre.SetValue("   ")
        _pulsar(dialogo.boton_guardar)
        assert avisos != []
        assert dialogo.nombre is None

        dialogo.campo_nombre.SetValue("hobby")
        _pulsar(dialogo.boton_guardar)
        assert dialogo.nombre == "hobby"
    finally:
        dialogo.Destroy()


def test_dialogo_nombre_etiqueta_rechaza_un_nombre_prohibido(app, monkeypatch):
    avisos = []
    monkeypatch.setattr(
        dialogo_gestion_etiquetas, "avisar", lambda texto, titulo, padre: avisos.append(texto)
    )
    dialogo = dialogo_gestion_etiquetas.DialogoNombreEtiqueta(
        None, "Añadir etiqueta", "&Añadir", nombres_prohibidos=["ocio"]
    )
    dialogo.EndModal = lambda codigo: None
    try:
        dialogo.campo_nombre.SetValue("ocio")
        _pulsar(dialogo.boton_guardar)
        assert avisos != []
        assert dialogo.nombre is None
    finally:
        dialogo.Destroy()


def test_ventana_principal_renombrar_y_eliminar_etiqueta_afecta_a_todos_los_enlaces(app, almacen):
    almacen.marcar_pendiente(nuevo_elemento_local("https://a.com", etiquetas=("ocio", "trabajo")))
    almacen.marcar_pendiente(nuevo_elemento_local("https://b.com", etiquetas=("ocio",)))
    almacen.marcar_pendiente(nuevo_elemento_local("https://c.com", etiquetas=("trabajo",)))
    ventana = _ventana(almacen)
    try:
        ventana._al_renombrar_etiqueta("ocio", "hobby")
        vivos = elementos_visibles(almacen.cargar_todos())
        assert sorted(e.etiquetas for e in vivos if "hobby" in e.etiquetas or "ocio" in e.etiquetas) == [
            ("hobby",),
            ("hobby", "trabajo"),
        ]

        ventana._al_eliminar_etiqueta("trabajo")
        vivos = elementos_visibles(almacen.cargar_todos())
        assert all("trabajo" not in e.etiquetas for e in vivos)
    finally:
        ventana.Destroy()


def test_ventana_principal_anadir_etiqueta_la_deja_pendiente_y_reservada(app, almacen):
    ventana = _ventana(almacen)
    try:
        ventana._al_anadir_etiqueta("vacaciones")

        reservadas = almacen.cargar_etiquetas_definidas()
        assert [e.nombre for e in reservadas.values()] == ["vacaciones"]
        assert almacen.cargar_etiquetas_pendientes() == reservadas
    finally:
        ventana.Destroy()


def test_ventana_principal_renombrar_y_eliminar_tambien_afecta_a_la_reservada(app, almacen):
    # Si la etiqueta que se renombra/elimina tambien tenia un registro reservado
    # (por ejemplo, se creo con "Anadir etiqueta nueva" antes de usarla en ningun
    # enlace), no debe quedar suelta con el nombre antiguo.
    almacen.marcar_pendiente(nuevo_elemento_local("https://a.com", etiquetas=("ocio",)))
    ventana = _ventana(almacen)
    try:
        ventana._al_anadir_etiqueta("ocio")
        ventana._al_renombrar_etiqueta("ocio", "hobby")

        reservadas = [e for e in almacen.cargar_etiquetas_definidas().values() if not e.borrado]
        assert [e.nombre for e in reservadas] == ["hobby"]

        ventana._al_eliminar_etiqueta("hobby")
        reservadas = [e for e in almacen.cargar_etiquetas_definidas().values() if not e.borrado]
        assert reservadas == []
    finally:
        ventana.Destroy()
