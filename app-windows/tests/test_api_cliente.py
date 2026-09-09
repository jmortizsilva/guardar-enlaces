from unittest.mock import MagicMock, patch

import pytest
import requests

from guardar_enlaces.api_cliente import ClienteApi, ErrorApi


def _respuesta(status_code=200, cuerpo=None):
    r = MagicMock(spec=requests.Response)
    r.status_code = status_code
    r.content = b"{}" if cuerpo is None else b"algo"
    r.json.return_value = cuerpo if cuerpo is not None else {}
    return r


@pytest.fixture
def cliente():
    return ClienteApi("http://localhost:8081")


class TestPeticionesCorrectas:
    def test_dev_login_llama_a_la_ruta_correcta(self, cliente):
        with patch("requests.post", return_value=_respuesta(200, {"tokenAcceso": "t"})) as mock_post:
            resultado = cliente.dev_login("a@b.com")

        mock_post.assert_called_once()
        args, kwargs = mock_post.call_args
        assert args[0] == "http://localhost:8081/auth/dev-login"
        assert kwargs["json"] == {"email": "a@b.com"}
        assert resultado == {"tokenAcceso": "t"}

    def test_pull_manda_el_token_en_la_cabecera_authorization(self, cliente):
        with patch("requests.get", return_value=_respuesta(200, {"elementos": []})) as mock_get:
            cliente.pull(desde=100, token_acceso="tok123", limite=50)

        args, kwargs = mock_get.call_args
        assert kwargs["headers"]["Authorization"] == "Bearer tok123"
        assert kwargs["params"] == {"desde": 100, "limite": 50}

    def test_push_manda_el_lote_de_elementos(self, cliente):
        lote = [{"id": "e1", "url": "https://a.com", "actualizadoEn": 100}]
        with patch("requests.post", return_value=_respuesta(200, {"elementos": lote})) as mock_post:
            resultado = cliente.push(lote, token_acceso="tok")

        assert mock_post.call_args.kwargs["json"] == {"elementos": lote}
        assert resultado == {"elementos": lote}

    def test_url_base_con_barra_final_no_duplica_la_barra(self):
        cliente = ClienteApi("http://localhost:8081/")
        with patch("requests.post", return_value=_respuesta(200, {})) as mock_post:
            cliente.logout("tok")
        assert mock_post.call_args.args[0] == "http://localhost:8081/auth/logout"


class TestErrores:
    def test_un_403_lanza_errorapi_con_el_mensaje_del_servidor(self, cliente):
        with patch("requests.post", return_value=_respuesta(403, {"error": "sesion no valida"})):
            with pytest.raises(ErrorApi) as exc:
                cliente.dev_login("nadie@x.com")
        assert "sesion no valida" in str(exc.value)
        assert exc.value.status_code == 403

    def test_un_fallo_de_red_lanza_errorapi(self, cliente):
        with patch("requests.get", side_effect=requests.ConnectionError("rechazado")):
            with pytest.raises(ErrorApi) as exc:
                cliente.pull(desde=0, token_acceso="tok")
        assert "no se pudo conectar" in str(exc.value)

    def test_un_error_sin_cuerpo_json_no_revienta(self, cliente):
        r = MagicMock(spec=requests.Response)
        r.status_code = 500
        r.content = b"<html>error</html>"
        r.json.side_effect = ValueError("no es json")
        with patch("requests.post", return_value=r):
            with pytest.raises(ErrorApi) as exc:
                cliente.logout("tok")
        assert "500" in str(exc.value)
