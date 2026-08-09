from unittest.mock import patch

import keyring.errors

from guardar_enlaces import credenciales


def test_guardar_y_obtener_token_refresco():
    with patch("keyring.set_password") as mock_set:
        credenciales.guardar_token_refresco("abc123")
    mock_set.assert_called_once_with("guardar-enlaces", "token-refresco", "abc123")

    with patch("keyring.get_password", return_value="abc123") as mock_get:
        assert credenciales.obtener_token_refresco() == "abc123"
    mock_get.assert_called_once_with("guardar-enlaces", "token-refresco")


def test_obtener_sin_nada_guardado_devuelve_none():
    with patch("keyring.get_password", return_value=None):
        assert credenciales.obtener_token_refresco() is None


def test_borrar_no_revienta_si_no_habia_nada_guardado():
    with patch("keyring.delete_password", side_effect=keyring.errors.PasswordDeleteError()):
        credenciales.borrar_token_refresco()  # no debe lanzar
