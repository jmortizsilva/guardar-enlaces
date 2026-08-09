from unittest.mock import MagicMock, patch

import pytest

from guardar_enlaces.api_cliente import ErrorApi
from guardar_enlaces.sesion import Sesion


@pytest.fixture
def cliente():
    return MagicMock()


@pytest.fixture
def sesion(cliente):
    return Sesion(cliente)


def test_iniciar_con_dev_login_guarda_token_acceso_usuario_y_refresco(sesion, cliente):
    cliente.dev_login.return_value = {
        "tokenAcceso": "acc1",
        "tokenRefresco": "ref1",
        "usuario": {"id": 1, "email": "a@b.com"},
    }
    with patch("guardar_enlaces.credenciales.guardar_token_refresco") as mock_guardar:
        sesion.iniciar_con_dev_login("a@b.com")

    assert sesion.autenticado
    assert sesion.token_acceso == "acc1"
    assert sesion.usuario == {"id": 1, "email": "a@b.com"}
    mock_guardar.assert_called_once_with("ref1")


def test_restaurar_sin_token_guardado_devuelve_false(sesion):
    with patch("guardar_enlaces.credenciales.obtener_token_refresco", return_value=None):
        assert sesion.restaurar() is False
    assert not sesion.autenticado


def test_restaurar_con_token_valido_renueva_la_sesion(sesion, cliente):
    cliente.renovar.return_value = {"tokenAcceso": "acc2", "tokenRefresco": "ref2"}
    with patch("guardar_enlaces.credenciales.obtener_token_refresco", return_value="ref1"), \
         patch("guardar_enlaces.credenciales.guardar_token_refresco") as mock_guardar:
        assert sesion.restaurar() is True

    assert sesion.token_acceso == "acc2"
    mock_guardar.assert_called_once_with("ref2")


def test_restaurar_con_token_caducado_lo_borra_y_devuelve_false(sesion, cliente):
    cliente.renovar.side_effect = ErrorApi("caducado", status_code=401)
    with patch("guardar_enlaces.credenciales.obtener_token_refresco", return_value="ref1"), \
         patch("guardar_enlaces.credenciales.borrar_token_refresco") as mock_borrar:
        assert sesion.restaurar() is False
    mock_borrar.assert_called_once()


def test_con_reintento_no_hace_nada_especial_si_no_hay_error(sesion):
    sesion.token_acceso = "acc1"
    resultado = sesion.con_reintento(lambda token: f"ok-{token}")
    assert resultado == "ok-acc1"


def test_con_reintento_renueva_una_vez_tras_un_401_y_reintenta(sesion, cliente):
    sesion.token_acceso = "acc-caducado"
    cliente.renovar.return_value = {"tokenAcceso": "acc-nuevo", "tokenRefresco": "ref-nuevo"}

    llamadas = []

    def funcion(token):
        llamadas.append(token)
        if token == "acc-caducado":
            raise ErrorApi("caducado", status_code=401)
        return "listo"

    with patch("guardar_enlaces.credenciales.obtener_token_refresco", return_value="ref-viejo"), \
         patch("guardar_enlaces.credenciales.guardar_token_refresco"):
        resultado = sesion.con_reintento(funcion)

    assert resultado == "listo"
    assert llamadas == ["acc-caducado", "acc-nuevo"]


def test_con_reintento_propaga_un_error_que_no_es_401(sesion):
    sesion.token_acceso = "acc1"
    with pytest.raises(ErrorApi):
        sesion.con_reintento(lambda token: (_ for _ in ()).throw(ErrorApi("fallo", 500)))


def test_cerrar_borra_el_token_y_limpia_el_estado(sesion, cliente):
    sesion.token_acceso = "acc1"
    sesion.usuario = {"id": 1}
    with patch("guardar_enlaces.credenciales.obtener_token_refresco", return_value="ref1"), \
         patch("guardar_enlaces.credenciales.borrar_token_refresco") as mock_borrar:
        sesion.cerrar()

    cliente.logout.assert_called_once_with("ref1")
    mock_borrar.assert_called_once()
    assert sesion.token_acceso is None
    assert sesion.usuario is None
