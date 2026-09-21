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
# Este .cmd corre SIN CONSOLA, y ahi media linea de comandos de Windows deja de
# comportarse como uno esperaria. De ahi que no haya ningun bucle de espera: el
# que esperaba es lo que estaba roto, y quien espera ahora es robocopy.
#
# Lo aprendido, midiendolo, y por lo que cada linea esta como esta:
#
# - `tasklist` NO DEVUELVE NADA sin consola: el fichero de salida queda vacio,
#   asi que esperar a que el proceso desaparezca era esperar a algo que nunca se
#   veia. Y con `tasklist | find` era peor: find se quedaba colgado PARA SIEMPRE
#   esperando una entrada que no iba a llegar. El relevo no pasaba de la primera
#   linea del registro, nunca copiaba nada y nunca relanzaba: desde fuera, la
#   aplicacion se cerraba al actualizar, no volvia, y la version vieja seguia
#   entera. Eso es lo que fallaba.
# - Probar el bloqueo del .exe con el truco de redirigir encima tampoco vale:
#   responde "libre" aunque el fichero este bloqueado de verdad.
# - `timeout` falla al instante (codigo 125) porque sin consola no puede leer
#   del teclado. `ping -n` contra la direccion local es la espera que si
#   funciona, y por eso es la unica que se usa.
# - `robocopy` si se porta bien sin consola, y ya sabe esperar: /R reintenta
#   mientras el fichero siga bloqueado y /W dice cuanto esperar entre intentos.
#   Un minuto de reintentos cubre de sobra lo que tarda en cerrarse una ventana.
#   Ademas considera exito cualquier codigo menor que 8, de ahi que no se
#   compruebe con un "if errorlevel" al uso.
#
# El registro es lo unico que queda si algo vuelve a fallar: a partir del cierre
# ya no hay ninguna ventana donde contarlo.
_RELEVO = """@echo off
> "%~dp0registro.txt" echo Relevo iniciado, dando tiempo a que se cierre la ventana
ping -n {segundos_de_gracia} 127.0.0.1 >nul
>> "%~dp0registro.txt" echo Copiando encima (robocopy reintenta si sigue bloqueado)
robocopy "{origen}" "{destino}" /E /R:{reintentos} /W:1 /NFL /NDL /NJH /NJS /NC /NS >> "%~dp0registro.txt"
>> "%~dp0registro.txt" echo robocopy devolvio %errorlevel% (menos de 8 es correcto)
start "" "{destino}\\{exe}"
>> "%~dp0registro.txt" echo Relanzada
"""

# ping -n 4 son tres segundos: lo que tarda en cerrarse una ventana, con margen.
SEGUNDOS_DE_GRACIA = 4
# Con /W:1, esto es aproximadamente un minuto esperando a que suelte el .exe.
REINTENTOS = 60


NOMBRE_MARCA_VERSION = "version-vista.txt"


def estrena_version(version_actual: str, marca: Path) -> bool:
    """True si esta version NO es la que se vio la ultima vez, o sea: acaban
    de actualizar la aplicacion. Deja constancia para la proxima.

    Esta senal la comprueba el codigo NUEVO, que es lo que la hace util: el
    relevo lo escribe la version vieja, asi que cualquier marca que pusiera el
    guion solo funcionaria una actualizacion mas tarde. Ademas pilla tambien
    al que sustituye la carpeta a mano.

    La primera vez de todas no cuenta como estreno: no hay de donde venir, y
    anunciar "actualizada" al estrenarla seria mentira.
    """
    try:
        vista = marca.read_text(encoding="utf-8").strip()
    except (OSError, ValueError):
        # ValueError cubre UnicodeDecodeError, que NO es un OSError. Paso por
        # aqui: una marca escrita en UTF-16 --el Out-File de PowerShell 5.1 lo
        # hace-- reventaba la lectura, y como esto corre en OnInit se llevaba
        # por delante el arranque entero. La aplicacion no abria, sin ventana
        # y sin aviso, por un fichero de pista que no deberia importar tanto.
        vista = ""
    if vista != version_actual:
        try:
            marca.parent.mkdir(parents=True, exist_ok=True)
            marca.write_text(version_actual, encoding="utf-8")
        except OSError:
            pass  # sin poder escribir se avisara de mas, nunca de menos
    return bool(vista) and vista != version_actual


def guion_de_relevo(
    carpeta_nueva: Path,
    destino: Path,
    reintentos: int = REINTENTOS,
) -> str:
    """El .cmd que hara el relevo. Aparte para poder comprobarlo sin lanzarlo."""
    return _RELEVO.format(
        exe=NOMBRE_EXE,
        origen=carpeta_nueva,
        destino=destino,
        segundos_de_gracia=SEGUNDOS_DE_GRACIA,
        reintentos=reintentos,
    )


def aplicar(carpeta_nueva: Path, destino: Path | None = None) -> Path:
    """Lanza el relevo y devuelve el control: quien llama debe cerrar la
    aplicacion inmediatamente despues. Si tarda, el relevo aguanta --robocopy
    reintenta mientras el .exe siga bloqueado--, pero no eternamente.

    Devuelve la ruta del registro que va dejando, que es la unica forma de
    saber por donde fallo si la aplicacion no vuelve a abrirse.
    """
    destino = destino or carpeta_instalacion()
    carpeta_guion = Path(tempfile.mkdtemp(prefix="guardar-enlaces-relevo-"))
    guion = carpeta_guion / "relevo.cmd"
    guion.write_text(guion_de_relevo(carpeta_nueva, destino), encoding="cp1252")
    # DETACHED_PROCESS: el .cmd tiene que sobrevivir a que esta aplicacion muera,
    # que es justo lo que esta esperando. Y sin ventana negra.
    #
    # Las tres entradas/salidas a DEVNULL no son adorno: empaquetada, la
    # aplicacion no tiene consola y sus descriptores no valen nada, asi que lo
    # que lance el relevo heredaba handles muertos. Con DEVNULL hereda algo
    # valido, que es la mitad de los cuelgues raros de aqui.
    subprocess.Popen(
        ["cmd", "/c", str(guion)],
        creationflags=subprocess.DETACHED_PROCESS | subprocess.CREATE_NO_WINDOW,
        close_fds=True,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
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
