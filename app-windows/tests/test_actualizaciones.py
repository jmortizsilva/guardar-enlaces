"""Lo comprobable sin red ni ejecutable: la comparacion de versiones, la
validacion del manifiesto y la verificacion del zip descargado.

Lo que NO cubre ninguna prueba, y hay que probar ejecutando: el relevo (que el
.cmd espere, copie y vuelva a abrir la aplicacion).
"""

import hashlib
import zipfile
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
import requests

from guardar_enlaces.actualizaciones import (
    VersionDisponible,
    comprobar,
    descargar,
    descomprimir,
    hay_version_nueva,
    leer_manifiesto,
)


class TestHayVersionNueva:
    def test_una_version_mayor_si(self):
        assert hay_version_nueva("1.0.0", "1.1.0")
        assert hay_version_nueva("1.9.0", "1.10.0")  # no es comparacion de texto

    def test_la_misma_no(self):
        assert not hay_version_nueva("1.0.0", "1.0.0")

    def test_una_anterior_tampoco(self):
        # Publicar por error una release vieja no debe "actualizarte" hacia atras.
        assert not hay_version_nueva("2.0.0", "1.9.9")

    def test_una_version_con_letras_se_ignora(self):
        assert not hay_version_nueva("1.0.0", "1.1.0-beta")


class TestLeerManifiesto:
    def _valido(self, **cambios):
        datos = {
            "version": "1.1.0",
            "url": "https://github.com/x/y/releases/download/v1.1.0/app.zip",
            "sha256": "a" * 64,
            "novedades": "Cosas nuevas",
        }
        datos.update(cambios)
        return datos

    def test_un_manifiesto_completo_se_lee(self):
        leido = leer_manifiesto(self._valido())

        assert leido == VersionDisponible(
            version="1.1.0",
            url="https://github.com/x/y/releases/download/v1.1.0/app.zip",
            sha256="a" * 64,
            novedades="Cosas nuevas",
        )

    def test_sin_algun_campo_se_descarta(self):
        datos = self._valido()
        del datos["sha256"]

        assert leer_manifiesto(datos) is None

    def test_una_url_que_no_sea_https_se_descarta(self):
        assert leer_manifiesto(self._valido(url="http://ejemplo.com/app.zip")) is None

    def test_un_sha256_de_longitud_rara_se_descarta(self):
        assert leer_manifiesto(self._valido(sha256="abc")) is None


class TestComprobar:
    def test_si_el_servidor_no_responde_no_se_actualiza_ni_se_lanza(self):
        with patch("requests.get", side_effect=requests.ConnectionError("sin red")):
            assert comprobar("1.0.0") is None

    def test_con_una_version_nueva_la_devuelve(self):
        respuesta = MagicMock(status_code=200)
        respuesta.json.return_value = {
            "version": "2.0.0",
            "url": "https://github.com/x/y/releases/download/v2.0.0/app.zip",
            "sha256": "b" * 64,
            "novedades": "",
        }
        with patch("requests.get", return_value=respuesta):
            disponible = comprobar("1.0.0")

        assert disponible is not None
        assert disponible.version == "2.0.0"

    def test_con_la_misma_version_no_ofrece_nada(self):
        respuesta = MagicMock(status_code=200)
        respuesta.json.return_value = {
            "version": "1.0.0",
            "url": "https://github.com/x/y/releases/download/v1.0.0/app.zip",
            "sha256": "b" * 64,
            "novedades": "",
        }
        with patch("requests.get", return_value=respuesta):
            assert comprobar("1.0.0") is None


class TestDescargar:
    def _respuesta_con(self, contenido: bytes) -> MagicMock:
        respuesta = MagicMock()
        respuesta.__enter__ = lambda s: s
        respuesta.__exit__ = lambda *a: False
        respuesta.iter_content.return_value = [contenido]
        return respuesta

    def test_un_zip_integro_se_acepta(self, tmp_path: Path):
        contenido = b"contenido de prueba"
        disponible = VersionDisponible(
            version="1.1.0",
            url="https://ejemplo.com/app.zip",
            sha256=hashlib.sha256(contenido).hexdigest(),
            novedades="",
        )
        destino = tmp_path / "app.zip"

        with patch("requests.get", return_value=self._respuesta_con(contenido)):
            assert descargar(disponible, destino)
        assert destino.read_bytes() == contenido

    def test_un_zip_que_no_cuadra_se_rechaza(self, tmp_path: Path):
        # Una descarga a medias o corrompida: es el fallo que esto detecta.
        disponible = VersionDisponible(
            version="1.1.0",
            url="https://ejemplo.com/app.zip",
            sha256="c" * 64,
            novedades="",
        )

        with patch("requests.get", return_value=self._respuesta_con(b"otra cosa")):
            assert not descargar(disponible, tmp_path / "app.zip")


class TestDescomprimir:
    def test_encuentra_la_carpeta_del_ejecutable_aunque_este_anidada(self, tmp_path: Path):
        zip_path = tmp_path / "app.zip"
        with zipfile.ZipFile(zip_path, "w") as z:
            z.writestr("GuardarEnlaces/GuardarEnlaces.exe", "no es un exe de verdad")
            z.writestr("GuardarEnlaces/_internal/algo.dll", "tampoco")

        carpeta = descomprimir(zip_path)

        assert carpeta is not None
        assert (carpeta / "GuardarEnlaces.exe").exists()

    def test_un_zip_sin_ejecutable_no_vale(self, tmp_path: Path):
        zip_path = tmp_path / "app.zip"
        with zipfile.ZipFile(zip_path, "w") as z:
            z.writestr("leeme.txt", "aqui no hay aplicacion")

        assert descomprimir(zip_path) is None

    def test_un_fichero_que_no_es_un_zip_no_revienta(self, tmp_path: Path):
        falso = tmp_path / "app.zip"
        falso.write_bytes(b"esto no es un zip")

        assert descomprimir(falso) is None


@pytest.fixture(autouse=True)
def _sin_escribir_en_el_sistema():
    """Salvaguarda: ninguna prueba de este fichero debe lanzar el relevo."""
    with patch("subprocess.Popen") as popen:
        yield
        popen.assert_not_called()
