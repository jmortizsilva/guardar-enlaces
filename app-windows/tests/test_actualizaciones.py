"""Lo comprobable sin red ni ejecutable: la comparacion de versiones, la
validacion del manifiesto y la verificacion del zip descargado.

Del relevo se comprueba aqui el guion que se escribe, no lo que hace: que
copie de verdad y vuelva a abrir la aplicacion hay que probarlo ejecutandolo.
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
    estrena_version,
    guion_de_relevo,
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


class TestGuionDeRelevo:
    def _guion(self, **cambios) -> str:
        argumentos = {
            "carpeta_nueva": Path(r"C:\Temp\nueva"),
            "destino": Path(r"C:\Programas\GuardarEnlaces"),
        }
        argumentos.update(cambios)
        return guion_de_relevo(**argumentos)

    def test_no_hay_ningun_bucle_de_espera_propio(self):
        """Esta es LA prueba de este fichero. Sin consola, tasklist no devuelve
        nada y "tasklist | find" ademas dejaba a find colgado para siempre: el
        relevo no pasaba de la primera linea del registro, nunca copiaba y nunca
        relanzaba. Quien espera ahora es robocopy, que ahi si funciona."""
        guion = self._guion()

        assert "tasklist" not in guion
        assert "goto" not in guion
        for linea in guion.splitlines():
            assert "|" not in linea, f"vuelve a haber una tuberia: {linea}"

    def test_robocopy_reintenta_mientras_el_exe_siga_bloqueado(self):
        # Es lo unico que hace que la copia ocurra pese al bloqueo de Windows.
        assert "/R:60 /W:1" in self._guion()
        assert "/R:3 /W:1" in self._guion(reintentos=3)

    def test_duerme_con_ping_que_es_lo_que_funciona_sin_consola(self):
        guion = self._guion()

        assert "ping -n 4 127.0.0.1" in guion
        assert "timeout" not in guion

    def test_copia_lo_nuevo_encima_y_vuelve_a_abrir(self):
        guion = self._guion()

        assert r'robocopy "C:\Temp\nueva" "C:\Programas\GuardarEnlaces"' in guion
        assert r'start "" "C:\Programas\GuardarEnlaces\GuardarEnlaces.exe"' in guion

    def test_deja_registro_de_cada_paso(self):
        # Al fallar no hay ninguna ventana donde contarlo: el registro es lo
        # unico que queda para saber por donde se quedo.
        guion = self._guion()

        assert guion.count("registro.txt") >= 4


@pytest.fixture(autouse=True)
def _sin_escribir_en_el_sistema():
    """Salvaguarda: ninguna prueba de este fichero debe lanzar el relevo."""
    with patch("subprocess.Popen") as popen:
        yield
        popen.assert_not_called()


class TestEstrenaVersion:
    """Como sabe la aplicacion que acaban de actualizarla.

    La senal es que la version que arranca no sea la ultima que se vio, y no
    una marca que ponga el relevo: el relevo lo escribe la version VIEJA, asi
    que una marca suya solo serviria una actualizacion mas tarde. Esta la
    comprueba el codigo nuevo, asi que funciona ya en la primera.
    """

    def test_la_primera_vez_de_todas_no_es_un_estreno(self, tmp_path: Path):
        """Anunciar "actualizada" al estrenarla seria mentira."""
        assert not estrena_version("1.2.0", tmp_path / "version-vista.txt")

    def test_abrirla_otra_vez_igual_no_es_un_estreno(self, tmp_path: Path):
        marca = tmp_path / "version-vista.txt"
        estrena_version("1.2.0", marca)
        assert not estrena_version("1.2.0", marca)

    def test_una_version_distinta_si_lo_es(self, tmp_path: Path):
        marca = tmp_path / "version-vista.txt"
        estrena_version("1.2.0", marca)
        assert estrena_version("1.2.1", marca)

    def test_y_solo_se_avisa_una_vez(self, tmp_path: Path):
        marca = tmp_path / "version-vista.txt"
        estrena_version("1.2.0", marca)
        assert estrena_version("1.2.1", marca)
        assert not estrena_version("1.2.1", marca)

    def test_tambien_pilla_volver_a_una_version_anterior(self, tmp_path: Path):
        """Sustituir la carpeta a mano por una vieja tambien es un cambio."""
        marca = tmp_path / "version-vista.txt"
        estrena_version("1.2.1", marca)
        assert estrena_version("1.2.0", marca)

    def test_si_no_se_puede_escribir_no_revienta(self, tmp_path: Path):
        """Sin poder dejar constancia se avisara de mas, nunca de menos."""
        carpeta = tmp_path / "soy-un-fichero"
        carpeta.write_text("estorbo", encoding="utf-8")
        assert not estrena_version("1.2.0", carpeta / "version-vista.txt")

    def test_el_guion_del_relevo_no_lleva_ninguna_marca(self):
        """Se probo a que el relevo pasara un argumento y se descarto: lo
        escribe la version vieja. Que no vuelva a colarse."""
        guion = guion_de_relevo(Path("C:/nueva"), Path("C:/destino"))
        assert "--tras-actualizar" not in guion


class TestUnaMarcaIlegibleNoTumbaLaAplicacion:
    """estrena_version corre dentro de OnInit: lo que lance aqui deja a la
    aplicacion sin abrir, sin ventana y sin aviso. Un fichero de pista no
    puede tener ese poder."""

    def test_en_utf16_no_revienta(self, tmp_path: Path):
        """Como lo escribe el Out-File de PowerShell 5.1. Paso de verdad."""
        marca = tmp_path / "version-vista.txt"
        marca.write_bytes(b"\xff\xfe1\x00.\x002\x00.\x000\x00")
        assert estrena_version("1.2.1", marca) is False

    def test_con_basura_binaria_tampoco(self, tmp_path: Path):
        marca = tmp_path / "version-vista.txt"
        marca.write_bytes(b"\x00\x01\x02\xff\xfe\xfd")
        assert estrena_version("1.2.1", marca) is False

    def test_y_despues_se_deja_legible(self, tmp_path: Path):
        """No basta con no reventar: hay que dejarla arreglada, o el mismo
        fichero roto seguiria ahi para siempre."""
        marca = tmp_path / "version-vista.txt"
        marca.write_bytes(b"\xff\xfe1\x00.\x002\x00.\x000\x00")
        estrena_version("1.2.1", marca)
        assert marca.read_text(encoding="utf-8") == "1.2.1"
