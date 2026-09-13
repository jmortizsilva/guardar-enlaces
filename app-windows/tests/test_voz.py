from types import SimpleNamespace

from guardar_enlaces.voz import ScreenReaderPrism, SinScreenReader, Voz, elegir_screen_reader


class ScreenReaderQueApunta:
    def __init__(self):
        self.dicho = []

    def decir(self, texto):
        self.dicho.append(texto)


class ScreenReaderRoto:
    def decir(self, texto):
        raise RuntimeError("el lector se ha cerrado")


class BackendFalso:
    def __init__(self, nombre, con_braille=True):
        self.name = nombre
        self.features = SimpleNamespace(supports_output=con_braille)
        self.llamadas = []

    def output(self, texto, interrupt=False):
        self.llamadas.append(("output", texto))

    def speak(self, texto, interrupt=False):
        self.llamadas.append(("speak", texto))


class ContextoFalso:
    def __init__(self, backend=None):
        self._backend = backend

    def acquire_best(self):
        if self._backend is None:
            raise ValueError("Invalid or unsupported backend")
        return self._backend


def test_anuncia_el_texto():
    screen_reader = ScreenReaderQueApunta()
    assert Voz(screen_reader).anunciar("Eliminado: A")
    assert screen_reader.dicho == ["Eliminado: A"]


def test_sin_texto_no_habla():
    screen_reader = ScreenReaderQueApunta()
    assert not Voz(screen_reader).anunciar("")
    assert screen_reader.dicho == []


def test_si_el_lector_falla_no_tumba_la_aplicacion():
    assert not Voz(ScreenReaderRoto()).anunciar("Eliminado: A")


def test_una_voz_del_sistema_no_cuenta_como_lector():
    # Sin lector abierto, prism elige una de estas y hablaria en voz alta.
    for nombre in ("SAPI", "OneCore"):
        elegido = elegir_screen_reader(ContextoFalso(BackendFalso(nombre)))
        assert isinstance(elegido, SinScreenReader)


def test_sin_ningun_lector_disponible_no_habla():
    assert isinstance(elegir_screen_reader(ContextoFalso()), SinScreenReader)


def test_con_lector_manda_voz_y_braille_si_puede():
    backend = BackendFalso("NVDA")
    screen_reader = elegir_screen_reader(ContextoFalso(backend))
    assert isinstance(screen_reader, ScreenReaderPrism)
    screen_reader.decir("URL copiada")
    assert backend.llamadas == [("output", "URL copiada")]


def test_si_el_lector_no_tiene_braille_solo_habla():
    backend = BackendFalso("JAWS", con_braille=False)
    elegir_screen_reader(ContextoFalso(backend)).decir("URL copiada")
    assert backend.llamadas == [("speak", "URL copiada")]
