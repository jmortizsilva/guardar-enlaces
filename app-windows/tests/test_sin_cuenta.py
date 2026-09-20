"""El modo sin cuenta: la aplicacion funciona entera contra este equipo y lo
unico que no hace es sincronizar. Mismo reparto que en el iPhone.

Antes la cuenta era obligatoria aqui y no entrar cerraba la aplicacion, asi
que estas pruebas cubren estados que hasta ahora no existian.
"""

from __future__ import annotations

from dataclasses import dataclass
from unittest.mock import MagicMock

import pytest
import wx

from guardar_enlaces.almacen_local import AlmacenLocal
from guardar_enlaces.api_cliente import ErrorApi
from guardar_enlaces.asentar_cuenta import EnlacesEnElEquipo
from guardar_enlaces.modelo import nuevo_elemento_local
from guardar_enlaces.ui import dialogo_anadir as modulo_anadir
from guardar_enlaces.ui import preguntas as modulo_preguntas
from guardar_enlaces.ui import ventana_principal as modulo_ventana
from guardar_enlaces.ui.dialogo_anadir import DialogoAnadir
from guardar_enlaces.ui.ventana_principal import (
    _ETIQUETA_CERRAR_SESION,
    _ETIQUETA_ENTRAR,
    VentanaPrincipal,
)
from guardar_enlaces.voz import SinScreenReader, Voz

# Fijo y no un MagicMock: forma parte de la identidad del dueno de la cache,
# asi que las pruebas pueden comprobar exactamente que se guardo.
URL_SERVIDOR = "https://servidor.test"


def _cliente() -> MagicMock:
    cliente = MagicMock()
    cliente.url_base = URL_SERVIDOR
    return cliente


def _sesion(autenticado: bool) -> MagicMock:
    sesion = MagicMock()
    sesion.autenticado = autenticado
    sesion.usuario = {"email": "alguien@ejemplo.com"} if autenticado else None
    # Con cuenta, el constructor de la ventana lanza una sincronizacion: que
    # falle limpio en vez de reventar en el hilo de fondo con un cliente falso.
    sesion.con_reintento.side_effect = ErrorApi("sin red en el test")
    return sesion


def _ventana(almacen: AlmacenLocal, autenticado: bool) -> VentanaPrincipal:
    ventana = VentanaPrincipal(
        almacen, _cliente(), _sesion(autenticado), Voz(SinScreenReader())
    )
    # Que ninguna prueba dependa de una sincronizacion de verdad corriendo en
    # un hilo contra un cliente de mentira. Devuelve 0 rechazados y no un
    # MagicMock: el aviso posterior los compara con un numero.
    ventana._sincronizador = MagicMock()
    ventana._sincronizador.sincronizar.return_value = 0
    return ventana


@dataclass
class Dicho:
    """Lo que la aplicacion le conto al usuario al entrar."""

    preguntas: list[EnlacesEnElEquipo]
    avisos: list[str]


def _entrar(
    ventana: VentanaPrincipal,
    monkeypatch,
    correo: str = "nuevo@ejemplo.com",
    importar: bool = True,
) -> Dicho:
    """Entra con cuenta desde el menu, con el dialogo de login sustituido por
    uno que dice que si, y recogiendo todo lo que se le dice al usuario.

    La pregunta y el aviso se interceptan en preguntas.py, que es donde viven
    y desde donde los resuelve asentar_cuenta_contandolo.
    """
    dicho = Dicho(preguntas=[], avisos=[])

    class DialogoQueEntra:
        def __init__(self, padre, sesion, cliente):
            self._sesion = sesion

        def ShowModal(self):
            self._sesion.autenticado = True
            self._sesion.usuario = {"email": correo}
            return wx.ID_OK

        def Destroy(self):
            pass

    def preguntar(enlaces, padre=None):
        dicho.preguntas.append(enlaces)
        return importar

    monkeypatch.setattr(modulo_ventana, "DialogoLogin", DialogoQueEntra)
    monkeypatch.setattr(modulo_preguntas, "preguntar_importacion", preguntar)
    monkeypatch.setattr(
        modulo_preguntas, "avisar", lambda texto, titulo, padre=None, grave=False: dicho.avisos.append(texto)
    )
    ventana._al_entrar_con_cuenta()
    return dicho


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
            ventana.sincronizar_en_segundo_plano()
            ventana._sincronizador.sincronizar.assert_not_called()
        finally:
            ventana.Destroy()

    def test_pedirla_a_mano_explica_por_que_no_pasa_nada(self, app, almacen):
        """Callarse ante F5 parece una averia."""
        ventana = _ventana(almacen, autenticado=False)
        try:
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


class TestEntrarDespuesDeEstarSinCuenta:
    """El camino que hasta ahora no podia darse: guardar enlaces sin cuenta y
    entrar en una despues. asentar_cuenta ya sabia resolverlo --y su pregunta
    ya distinguia "sin cuenta" de "con otra cuenta"--, pero nadie la llamaba
    nunca desde este estado, porque este estado no existia.
    """

    def _con_enlaces(self, almacen: AlmacenLocal, cuantos: int) -> None:
        for i in range(cuantos):
            almacen.marcar_pendiente(
                nuevo_elemento_local(f"https://ejemplo.com/{i}", titulo=f"Enlace {i}")
            )

    def test_pregunta_por_los_que_ya_habia_y_sabe_que_no_son_de_nadie(
        self, app, almacen, monkeypatch
    ):
        self._con_enlaces(almacen, 2)
        ventana = _ventana(almacen, autenticado=False)
        try:
            dicho = _entrar(ventana, monkeypatch)
            assert dicho.preguntas == [EnlacesEnElEquipo(cuantos=2, de_otra_cuenta=False)]
        finally:
            ventana.Destroy()

    def test_anadirlos_los_deja_en_la_cola_de_subida(self, app, almacen, monkeypatch):
        """Adoptarlos no basta: si no entran en el outbox se quedan en este
        equipo para siempre y el movil no los ve nunca."""
        self._con_enlaces(almacen, 2)
        ventana = _ventana(almacen, autenticado=False)
        try:
            _entrar(ventana, monkeypatch, importar=True)

            assert almacen.contar_elementos() == 2
            assert len(almacen.cargar_pendientes()) == 2
            assert almacen.dueno_actual() == f"{URL_SERVIDOR}|nuevo@ejemplo.com"
        finally:
            ventana.Destroy()

    def test_borrarlos_deja_el_equipo_vacio(self, app, almacen, monkeypatch):
        self._con_enlaces(almacen, 3)
        ventana = _ventana(almacen, autenticado=False)
        try:
            _entrar(ventana, monkeypatch, importar=False)

            assert almacen.contar_elementos() == 0
            assert almacen.dueno_actual() == f"{URL_SERVIDOR}|nuevo@ejemplo.com"
        finally:
            ventana.Destroy()

    def test_sin_nada_guardado_no_pregunta(self, app, almacen, monkeypatch):
        """Entrar recien estrenada no tiene nada que decidir: preguntar ahi
        seria pedir permiso para no hacer nada."""
        ventana = _ventana(almacen, autenticado=False)
        try:
            dicho = _entrar(ventana, monkeypatch)

            assert dicho.preguntas == []
            assert almacen.dueno_actual() == f"{URL_SERVIDOR}|nuevo@ejemplo.com"
        finally:
            ventana.Destroy()

    def test_volver_a_la_cuenta_de_siempre_no_pregunta_ni_toca_nada(
        self, app, almacen, monkeypatch
    ):
        """El caso normal --cerrar sesion y volver a entrar-- no puede acabar
        preguntando si borrar lo que ya era tuyo."""
        almacen.fijar_dueno(f"{URL_SERVIDOR}|quien@ejemplo.com")
        self._con_enlaces(almacen, 2)
        ventana = _ventana(almacen, autenticado=False)
        try:
            dicho = _entrar(ventana, monkeypatch, correo="quien@ejemplo.com")

            assert dicho.preguntas == []
            assert almacen.contar_elementos() == 2
        finally:
            ventana.Destroy()

    def test_volver_a_la_cuenta_de_siempre_avisa_de_lo_que_se_va_a_subir(
        self, app, almacen, monkeypatch
    ):
        """Es el caso que parecia una averia: no preguntaba nada y los enlaces
        subian en silencio, asi que daba la impresion de que la aplicacion no
        se habia enterado de que existian."""
        almacen.fijar_dueno(f"{URL_SERVIDOR}|quien@ejemplo.com")
        self._con_enlaces(almacen, 2)
        ventana = _ventana(almacen, autenticado=False)
        try:
            dicho = _entrar(ventana, monkeypatch, correo="quien@ejemplo.com")

            assert dicho.preguntas == []
            assert len(dicho.avisos) == 1
            assert "2 enlaces" in dicho.avisos[0]
        finally:
            ventana.Destroy()

    def test_el_aviso_habla_en_singular_cuando_toca(self, app, almacen, monkeypatch):
        almacen.fijar_dueno(f"{URL_SERVIDOR}|quien@ejemplo.com")
        self._con_enlaces(almacen, 1)
        ventana = _ventana(almacen, autenticado=False)
        try:
            dicho = _entrar(ventana, monkeypatch, correo="quien@ejemplo.com")

            assert dicho.avisos == [
                "El enlace que guardaste sin haber iniciado sesión se va a "
                "subir a tu cuenta. También lo verás en el móvil."
            ]
        finally:
            ventana.Destroy()

    def test_si_hubo_pregunta_no_hay_ademas_aviso(self, app, almacen, monkeypatch):
        """Dos cuadros seguidos hablando de los mismos enlaces sobran: la
        pregunta ya conto lo que iba a pasar con ellos."""
        self._con_enlaces(almacen, 2)
        ventana = _ventana(almacen, autenticado=False)
        try:
            dicho = _entrar(ventana, monkeypatch, importar=True)

            assert len(dicho.preguntas) == 1
            assert dicho.avisos == []
        finally:
            ventana.Destroy()

    def test_sin_nada_pendiente_no_se_avisa_de_nada(self, app, almacen, monkeypatch):
        almacen.fijar_dueno(f"{URL_SERVIDOR}|quien@ejemplo.com")
        ventana = _ventana(almacen, autenticado=False)
        try:
            dicho = _entrar(ventana, monkeypatch, correo="quien@ejemplo.com")

            assert dicho.preguntas == []
            assert dicho.avisos == []
        finally:
            ventana.Destroy()

    def test_entrar_pone_al_dia_el_titulo_y_el_menu(self, app, almacen, monkeypatch):
        ventana = _ventana(almacen, autenticado=False)
        try:
            _entrar(ventana, monkeypatch, correo="quien@ejemplo.com")

            assert ventana.GetTitle() == "Guárdalo — quien@ejemplo.com"
            assert _etiqueta_de_cuenta(ventana) == _ETIQUETA_CERRAR_SESION
        finally:
            ventana.Destroy()

    def test_al_entrar_se_sincroniza_sin_esperar_al_freno(
        self, app, almacen, monkeypatch
    ):
        """Lo primero que quiere ver quien acaba de entrar es su biblioteca,
        no un cuarto de hora de espera. Y de paso comprueba que el guardian de
        la fase 2 deja pasar en cuanto hay cuenta."""
        ventana = _ventana(almacen, autenticado=False)
        try:
            _entrar(ventana, monkeypatch)
            assert ventana._ultima_sincronizacion > 0
        finally:
            ventana.Destroy()

    def test_si_no_se_llega_a_entrar_todo_sigue_como_estaba(
        self, app, almacen, monkeypatch
    ):
        """Abrir el dialogo y echarse atras no puede tocar los enlaces."""
        self._con_enlaces(almacen, 2)
        ventana = _ventana(almacen, autenticado=False)
        try:

            class DialogoQueSeCierra:
                def __init__(self, padre, sesion, cliente):
                    pass

                def ShowModal(self):
                    return wx.ID_CANCEL

                def Destroy(self):
                    pass

            monkeypatch.setattr(modulo_ventana, "DialogoLogin", DialogoQueSeCierra)
            ventana._al_entrar_con_cuenta()

            assert almacen.contar_elementos() == 2
            assert almacen.dueno_actual() is None
            assert _etiqueta_de_cuenta(ventana) == _ETIQUETA_ENTRAR
        finally:
            ventana.Destroy()
