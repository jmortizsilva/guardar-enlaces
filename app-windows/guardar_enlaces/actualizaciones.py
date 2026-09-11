"""Actualizacion a distancia del ejecutable, desde GitHub Releases.

Como funciona, y por que asi:

- El manifiesto y el zip se publican como ficheros sueltos de una *release*, y
  se piden por la URL fija `releases/latest/download/<fichero>`. No se usa la
  API de GitHub: esa URL no gasta el limite de peticiones por hora y no
  necesita ningun token, ahora que el repositorio es publico.
- El zip se verifica por SHA-256 contra lo que dice el manifiesto. Ambos llegan
  por HTTPS del mismo sitio, asi que no es una firma de verdad --quien pudiera
  alterar uno alteraria el otro--; lo que si detecta es una descarga a medias o
  corrompida, que es el fallo probable.
- Windows NO deja reemplazar el ejecutable de un programa en marcha, asi que el
  relevo lo hace un .cmd aparte: espera a que la aplicacion se cierre, copia los
  ficheros nuevos encima y la vuelve a abrir.

Nada de esto aplica al ejecutar desde el codigo fuente (`python -m
guardar_enlaces`): ahi se actualiza con git, y `esta_empaquetada()` lo detecta.
"""

from __future__ import annotations

import hashlib
import os
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path

import requests

from .version import VERSION

URL_MANIFIESTO = (
    "https://github.com/jmortizsilva/guardar-enlaces/releases/latest/download/ultima.json"
)
NOMBRE_EXE = "GuardarEnlaces.exe"
TIMEOUT_S = 30.0


@dataclass(frozen=True)
class VersionDisponible:
    version: str
    url: str
    sha256: str
    novedades: str


def esta_empaquetada() -> bool:
    """True si corre como ejecutable, False si es el codigo fuente."""
    return getattr(sys, "frozen", False)


def carpeta_instalacion() -> Path:
    """La carpeta que hay que sustituir: la del .exe, no la de los datos."""
    return Path(sys.executable).parent


def _como_numeros(version: str) -> tuple[int, ...]:
    partes = []
    for trozo in version.strip().split("."):
        try:
            partes.append(int(trozo))
        except ValueError:
            # Una version con letras (1.2.0-beta) no se compara a medias: se
            # trata como la mas baja posible, para no ofrecer una "actualizacion"
            # que en realidad es un retroceso.
            return (-1,)
    return tuple(partes)


def hay_version_nueva(actual: str, remota: str) -> bool:
    """Solo hacia adelante: si la de GitHub es igual o anterior, no se ofrece."""
    numeros_remota = _como_numeros(remota)
    if numeros_remota == (-1,):
        return False
    return numeros_remota > _como_numeros(actual)


def leer_manifiesto(datos: dict) -> VersionDisponible | None:
    """Valida lo que llega de la red antes de fiarse. Devuelve None si le falta
    algo: preferimos no actualizar a intentarlo con medio manifiesto."""
    try:
        version = str(datos["version"])
        url = str(datos["url"])
        sha256 = str(datos["sha256"]).lower()
        novedades = str(datos.get("novedades", ""))
    except (KeyError, TypeError):
        return None
    if not version or not url.startswith("https://") or len(sha256) != 64:
        return None
    return VersionDisponible(version=version, url=url, sha256=sha256, novedades=novedades)


def comprobar(
    version_actual: str = VERSION,
    url_manifiesto: str = URL_MANIFIESTO,
) -> VersionDisponible | None:
    """La version disponible si es mas nueva que esta; None en cualquier otro
    caso, incluido no haber podido preguntar. Nunca lanza: que falle la
    comprobacion no puede impedir usar la aplicacion."""
    try:
        respuesta = requests.get(url_manifiesto, timeout=TIMEOUT_S)
        if respuesta.status_code != 200:
            return None
        disponible = leer_manifiesto(respuesta.json())
    except (requests.RequestException, ValueError):
        return None
    if not disponible or not hay_version_nueva(version_actual, disponible.version):
        return None
    return disponible


def descargar(disponible: VersionDisponible, destino: Path) -> bool:
    """Descarga el zip y comprueba su SHA-256. False si no cuadra."""
    resumen = hashlib.sha256()
    try:
        with requests.get(disponible.url, timeout=TIMEOUT_S, stream=True) as respuesta:
            respuesta.raise_for_status()
            with open(destino, "wb") as fichero:
                for trozo in respuesta.iter_content(chunk_size=65536):
                    fichero.write(trozo)
                    resumen.update(trozo)
    except requests.RequestException:
        return False
    return resumen.hexdigest() == disponible.sha256


# El relevo. Espera a que la aplicacion se cierre (Windows no deja sustituir un
# .exe en marcha), copia lo nuevo encima y la vuelve a abrir.
#
# Dos cosas que parecen detalles y son las que hacen que funcione o no, las dos
# aprendidas midiendo un relevo que fallaba en silencio:
#
# - Se espera por IDENTIFICADOR de proceso, no por nombre. Esperar por nombre
#   fallaba: el bucle salia antes de que la aplicacion hubiera cerrado, robocopy
#   se encontraba el .exe todavia bloqueado y no lo sustituia. Como robocopy
#   escribia en nul, no se enteraba nadie: la aplicacion se cerraba y no volvia.
# - Se duerme con "ping" y no con "timeout". Este .cmd corre sin consola, y ahi
#   timeout falla al instante (codigo 125) porque no puede leer del teclado; el
#   bucle se convertia en una espera activa que ademas lanzaba un tasklist por
#   vuelta. ping -n 2 contra la direccion local es el segundo de espera clasico
#   que si funciona sin consola.
#
# robocopy considera exito cualquier codigo menor que 8, de ahi que no se
# compruebe con un "if errorlevel" al uso. /R:5 /W:1 reintenta por si algun
# fichero sigue bloqueado un instante mas.
_RELEVO = """@echo off
> "%~dp0registro.txt" echo Relevo iniciado, esperando a que cierre el proceso {pid}
:esperar
tasklist /FI "PID eq {pid}" 2>nul | find "{pid}" >nul
if not errorlevel 1 (
    ping -n 2 127.0.0.1 >nul
    goto esperar
)
>> "%~dp0registro.txt" echo La aplicacion ya se cerro
robocopy "{origen}" "{destino}" /E /R:5 /W:1 /NFL /NDL /NJH /NJS /NC /NS >> "%~dp0registro.txt"
>> "%~dp0registro.txt" echo robocopy devolvio %errorlevel% (menos de 8 es correcto)
start "" "{destino}\\{exe}"
>> "%~dp0registro.txt" echo Relanzada
"""


def aplicar(carpeta_nueva: Path, destino: Path | None = None) -> Path:
    """Lanza el relevo y devuelve el control: quien llama debe cerrar la
    aplicacion inmediatamente despues, o el .cmd se quedara esperando.

    Devuelve la ruta del registro que va dejando, que es la unica forma de
    saber por donde fallo si la aplicacion no vuelve a abrirse.
    """
    destino = destino or carpeta_instalacion()
    carpeta_guion = Path(tempfile.mkdtemp(prefix="guardar-enlaces-relevo-"))
    guion = carpeta_guion / "relevo.cmd"
    guion.write_text(
        _RELEVO.format(
            exe=NOMBRE_EXE,
            origen=carpeta_nueva,
            destino=destino,
            pid=os.getpid(),
        ),
        encoding="cp1252",
    )
    # DETACHED_PROCESS: el .cmd tiene que sobrevivir a que esta aplicacion muera,
    # que es justo lo que esta esperando. Y sin ventana negra.
    subprocess.Popen(
        ["cmd", "/c", str(guion)],
        creationflags=subprocess.DETACHED_PROCESS | subprocess.CREATE_NO_WINDOW,
        close_fds=True,
    )
    return carpeta_guion / "registro.txt"


def descomprimir(zip_descargado: Path) -> Path | None:
    """Deja el contenido en una carpeta temporal y devuelve la que contiene el
    ejecutable. None si el zip no trae lo que deberia."""
    carpeta = Path(tempfile.mkdtemp(prefix="guardar-enlaces-nueva-"))
    try:
        with zipfile.ZipFile(zip_descargado) as z:
            z.extractall(carpeta)
    except (zipfile.BadZipFile, OSError):
        return None

    for raiz, _, ficheros in os.walk(carpeta):
        if NOMBRE_EXE in ficheros:
            return Path(raiz)
    return None
