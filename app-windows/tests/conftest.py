import sys
import threading
import time
from pathlib import Path

import pytest

# Sin instalar el paquete (no hay setup.py/build backend), asi que hay que anadir app-windows/ al
# path explicitamente para que "import guardar_enlaces" funcione sea cual sea el cwd desde el que
# se invoque pytest. Todo lo de guardar_enlaces se importa DESPUES de esta linea, de ahi que las
# fixtures de aqui abajo importen por dentro.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


@pytest.fixture(scope="session")
def app():
    """UNA sola wx.App para toda la sesion de pruebas, y por eso vive aqui y
    no en cada fichero: dos objetos wx.App en el mismo proceso hacen que wx
    reviente al terminar, y no siempre, que es lo peor de todo."""
    import wx

    return wx.App()


def _esperar_hilos_de_fondo(limite_s: float = 3.0) -> None:
    """VentanaPrincipal sincroniza en hilos demonio, y uno puede seguir dentro
    del almacen cuando la prueba ya ha terminado. Si se cierra SQLite con un
    hilo ahi dentro, no salta una excepcion: se lleva por delante el proceso
    entero con una violacion de acceso, y solo a veces, asi que aparecia como
    una prueba distinta cada vez.
    """
    fin = time.monotonic() + limite_s
    for hilo in threading.enumerate():
        if hilo is threading.current_thread() or not hilo.is_alive():
            continue
        restante = fin - time.monotonic()
        if restante <= 0:
            break
        hilo.join(restante)


@pytest.fixture
def almacen():
    """Almacen en memoria. Vive aqui, y no en cada fichero de pruebas, porque
    cerrarlo bien exige esperar antes a los hilos de fondo."""
    from guardar_enlaces.almacen_local import AlmacenLocal

    a = AlmacenLocal(":memory:")
    yield a
    _esperar_hilos_de_fondo()
    a.cerrar()
