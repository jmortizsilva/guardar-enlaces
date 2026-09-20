"""El modo sin cuenta: la aplicacion funciona entera contra este equipo y lo
unico que no hace es sincronizar. Mismo reparto que en el iPhone.

Antes la cuenta era obligatoria aqui y no entrar cerraba la aplicacion, asi
que estas pruebas cubren estados que hasta ahora no existian.
"""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest
import wx

from guardar_enlaces.almacen_local import AlmacenLocal
from guardar_enlaces.api_cliente import ErrorApi
from guardar_enlaces.ui import dialogo_anadir as modulo_anadir
from guardar_enlaces.ui.dialogo_anadir import DialogoAnadir
from guardar_enlaces.ui.ventana_principal import (
    _ETIQUETA_CERRAR_SESION,
    _ETIQUETA_ENTRAR,
    VentanaPrincipal,
)
from guardar_enlaces.voz import SinScreenReader, Voz


def _sesion(autenticado: bool) -> MagicMock:
    sesion = MagicMock()
    sesion.autenticado = autenticado
    sesion.usuario = {"email": "alguien@ejemplo.com"} if autenticado else None
    # Con cuenta, el constructor de la ventana lanza una sincronizacion: que
    # falle limpio en vez de reventar en el hilo de fondo con un cliente falso.
    sesion.con_reintento.side_effect = ErrorApi("sin red en el test")
    return sesion


def _ventana(almacen: AlmacenLocal, autenticado: bool) -> VentanaPrincipal:
    return VentanaPrincipal(
        almacen, MagicMock(), _sesion(autenticado), Voz(SinScreenReader())
    )


def _etiqueta_de_cuenta(ventana: VentanaPrincipal) -> str:
    return ventana._menu_archivo.FindItemById(ventana._id_cuenta).GetItemLabel()


def _pulsar(boton: wx.Button) -> None:
    evento = wx.CommandEvent(wx.wxEVT_BUTTON, boton.GetId())
    evento.SetEventObject(boton)
    boton.GetEventHandler().ProcessEvent(evento)


class TestSincronizar:
    def test_sin_cuenta_no_se_sincroniza(self, app, almacen):
        """El guardian esta dentro de sincronizar_en_segundo_plano, que es lo
        que llaman los ocho sitios que disparan una sincronizacion."""
        ventana = _ventana(almacen, autenticado=False)
        try:
            ventana._sincronizador = MagicMock()
            ventana.sincronizar_en_segundo_plano()
            ventana._sincronizador.sincronizar.assert_not_called()
        finally:
            ventana.Destroy()

    def test_pedirla_a_mano_explica_por_que_no_pasa_nada(self, app, almacen):
        """Callarse ante F5 parece una averia."""
        ventana = _ventana(almacen, autenticado=False)
        try:
            ventana._sincronizador = MagicMock()
            ventana.sincronizar_en_segundo_plano(manual=True)
            dicho = ventana.GetStatusBar().GetStatusText().lower()
            assert "cuenta" in dicho
            ventana._sincronizador.sincronizar.assert_not_called()
        finally:
            ventana.Destroy()

    def test_la_automatica_se_calla(self, app, almacen):
        """Avisar en cada guardado seria ruido continuo en la barra."""
        ventana = _ventana(almacen, autenticado=False)
        try:
            ventana._sincronizador = MagicMock()
            ventana.SetStatusText("lo que hubiera antes")
            ventana.sincronizar_en_segundo_plano()
            assert ventana.GetStatusBar().GetStatusText() == "lo que hubiera antes"
        finally:
            ventana.Destroy()


class TestMenuDeCuenta:
    def test_sin_cuenta_ofrece_entrar(self, app, almacen):
        ventana = _ventana(almacen, autenticado=False)
        try:
            assert _etiqueta_de_cuenta(ventana) == _ETIQUETA_ENTRAR
        finally:
            ventana.Destroy()

    def test_con_cuenta_ofrece_salir(self, app, almacen):
        ventana = _ventana(almacen, autenticado=True)
        try:
            assert _etiqueta_de_cuenta(ventana) == _ETIQUETA_CERRAR_SESION
        finally:
            ventana.Destroy()

    def test_tras_cerrar_sesion_la_ventana_sigue_abierta_y_ofrece_entrar(
        self, app, almacen
    ):
        """Lo que antes cerraba la aplicacion. Los enlaces se quedan aqui, asi
        que hay de sobra que hacer sin cuenta."""
        ventana = _ventana(almacen, autenticado=True)
        try:
            ventana._sesion.autenticado = False
            ventana._sesion.usuario = None
            ventana._tras_cerrar_sesion()

            assert not ventana.IsBeingDeleted()
            assert _etiqueta_de_cuenta(ventana) == _ETIQUETA_ENTRAR
            assert ventana.GetTitle() == "Guárdalo"
        finally:
            ventana.Destroy()


class TestTitulo:
    def test_sin_cuenta_no_nombra_a_nadie(self, app, almacen):
        ventana = _ventana(almacen, autenticado=False)
        try:
            assert ventana.GetTitle() == "Guárdalo"
        finally:
            ventana.Destroy()


class TestDeDondeSalenLosMetadatos:
    """Con cuenta los resuelve el servidor, que es quien los guarda para los
    dos clientes; sin cuenta, este mismo equipo."""

    def _guardar_una_url(self, monkeypatch, autenticado: bool, resolver_local):
        # El guardado se va a un hilo y vuelve por wx.CallAfter. Aqui los dos
        # se ejecutan en el sitio, para que la prueba no dependa del reloj.
        class HiloInmediato:
            def __init__(self, target, daemon=False, args=(), kwargs=None):
                self._target = target

            def start(self):
                self._target()

        monkeypatch.setattr(modulo_anadir.threading, "Thread", HiloInmediato)
        monkeypatch.setattr(modulo_anadir.wx, "CallAfter", lambda f, *a, **k: f(*a, **k))
        monkeypatch.setattr(modulo_anadir, "resolver_en_este_equipo", resolver_local)

        cliente = MagicMock()
        sesion = MagicMock()
        sesion.autenticado = autenticado
        sesion.con_reintento.side_effect = lambda f: f("un-token")
        cliente.metadatos.return_value = {"titulo": "Lo dijo el servidor"}

        dialogo = DialogoAnadir(None, cliente, sesion)
        dialogo.EndModal = lambda codigo: None  # no esta abierto de verdad
        try:
            dialogo.campo_url.SetValue("https://ejemplo.com/a")
            _pulsar(dialogo.boton_guardar)
            return cliente, dialogo.elemento_creado
        finally:
            dialogo.Destroy()

    def test_sin_cuenta_los_resuelve_este_equipo(self, app, monkeypatch):
        pedidas = []

        def resolver(url):
            pedidas.append(url)
            return {"titulo": "Lo leyo este equipo", "tipo": "enlace"}

        cliente, elemento = self._guardar_una_url(monkeypatch, False, resolver)

        assert pedidas == ["https://ejemplo.com/a"]
        assert elemento is not None and elemento.titulo == "Lo leyo este equipo"
        cliente.metadatos.assert_not_called()

    def test_con_cuenta_los_resuelve_el_servidor(self, app, monkeypatch):
        def resolver(url):
            raise AssertionError("con cuenta no deberia resolverse aqui")

        cliente, elemento = self._guardar_una_url(monkeypatch, True, resolver)

        cliente.metadatos.assert_called_once()
        assert elemento is not None and elemento.titulo == "Lo dijo el servidor"

    def test_sin_cuenta_y_sin_red_el_enlace_se_guarda_igual(self, app, monkeypatch):
        """Lo mismo que ya pasaba cuando fallaba el servidor: guardar el
        enlace nunca depende de que se averigue el titulo."""
        cliente, elemento = self._guardar_una_url(monkeypatch, False, lambda url: {})

        assert elemento is not None
        assert elemento.url == "https://ejemplo.com/a"
        assert elemento.titulo is None
