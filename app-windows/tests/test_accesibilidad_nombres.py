"""No sustituye a probar con NVDA de verdad (eso hay que hacerlo a mano),
pero evita que alguien quite un SetName() sin darse cuenta: ver el hallazgo
en docs/ACCESIBILIDAD-WXPYTHON.md (wxWidgets/Phoenix#1129) -- sin nombre
explicito, NVDA anuncia el control como "edicion" a secas."""

from unittest.mock import MagicMock

import pytest
import wx

from guardar_enlaces.almacen_local import AlmacenLocal
from guardar_enlaces.api_cliente import ErrorApi
from guardar_enlaces.modelo import nuevo_elemento_local
from guardar_enlaces.ui.dialogo_anadir import DialogoAnadir
from guardar_enlaces.ui.dialogo_detalle import DialogoDetalle
from guardar_enlaces.ui.dialogo_login import DialogoLogin
from guardar_enlaces.ui.ventana_principal import VentanaPrincipal


@pytest.fixture(scope="module")
def app():
    return wx.App()


@pytest.fixture
def almacen():
    a = AlmacenLocal(":memory:")
    yield a
    a.cerrar()


def test_ventana_principal_buscador_y_selector_de_etiqueta_tienen_nombre(app, almacen):
    # El constructor lanza una sincronizacion en segundo plano; con una sesion mock que no
    # simula una respuesta real, que falle limpio con ErrorApi (evita ruido de una excepcion
    # sin capturar en el hilo de fondo, que no tiene nada que ver con este test).
    sesion = MagicMock()
    sesion.con_reintento.side_effect = ErrorApi("sin red en el test")
    ventana = VentanaPrincipal(almacen, MagicMock(), sesion)
    try:
        # Comprobamos el texto exacto, no solo "no vacio": wx da un nombre interno por
        # defecto a cada control (p.ej. "textCtrl"), que NO es un nombre accesible de verdad
        # y haria pasar el test aunque se borrase el SetName() por error.
        assert ventana.buscador.GetName() == "Buscar por título, URL o etiqueta"
        assert ventana.selector_etiqueta.GetName() == "Filtrar por etiqueta"
    finally:
        ventana.Destroy()


def test_dialogo_login_cuadro_estado_tiene_nombre(app):
    # El cuadro de estado es lo unico que NVDA puede leer al volver del
    # navegador (ver el comentario de dialogo_login.py), asi que su nombre
    # accesible importa mas que el de un campo normal.
    dialogo = DialogoLogin(None, MagicMock(), MagicMock())
    try:
        assert dialogo.estado.GetName() == "Estado del inicio de sesión"
    finally:
        dialogo.Destroy()


def test_dialogo_anadir_campo_url_tiene_nombre(app):
    dialogo = DialogoAnadir(None, MagicMock(), MagicMock())
    try:
        assert dialogo.campo_url.GetName() == "URL del enlace a añadir"
    finally:
        dialogo.Destroy()


def test_dialogo_detalle_campo_etiquetas_tiene_nombre(app):
    elemento = nuevo_elemento_local("https://a.com", titulo="A")
    dialogo = DialogoDetalle(None, elemento, lambda e: None, lambda e: None)
    try:
        assert dialogo.campo_etiquetas.GetName() == "Etiquetas, separadas por comas"
    finally:
        dialogo.Destroy()
