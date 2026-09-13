"""Decirle al lector de pantalla lo que no dice ningún control.

NVDA no lee la barra de estado cuando cambia, así que lo que se escribe ahí
(añadido, eliminado, un fallo al sincronizar) hay que decírselo. Debajo está
prism (paquete `prismatoid`), que habla con NVDA, JAWS y otros lectores y manda
el mismo texto a la línea braille. Si hubiera que cambiarlo, se cambia aquí.

Nada de esto puede tumbar la aplicación: sin prism, sin lector o con el lector
cerrado a media sesión, se sigue sin voz.
"""

from __future__ import annotations

from typing import Protocol

# Voces del sistema, no lectores de pantalla. prism las elige cuando no hay
# ningún lector abierto, y la aplicación le hablaría en voz alta a quien la usa
# sin lector.
VOCES_DEL_SISTEMA = frozenset({"SAPI", "OneCore"})


class ScreenReader(Protocol):
    def decir(self, texto: str) -> None: ...


class SinScreenReader:
    """No hay lector de pantalla abierto: no habla y no estorba."""

    def decir(self, texto: str) -> None:
        return None


class ScreenReaderPrism:
    def __init__(self, contexto, backend) -> None:
        # El contexto se guarda aunque no se use: al liberarlo, prism apaga sus
        # backends.
        self._contexto = contexto
        self._backend = backend
        # output manda voz y braille a la vez, pero no todos los lectores lo tienen.
        self._con_braille = bool(backend.features.supports_output)

    def decir(self, texto: str) -> None:
        if self._con_braille:
            self._backend.output(texto)
        else:
            self._backend.speak(texto)


def elegir_screen_reader(contexto) -> ScreenReader:
    """El lector de pantalla que esté abierto, o uno que no habla."""
    try:
        backend = contexto.acquire_best()
        if backend.name in VOCES_DEL_SISTEMA:
            return SinScreenReader()
        return ScreenReaderPrism(contexto, backend)
    except Exception:
        return SinScreenReader()


def buscar_screen_reader() -> ScreenReader:
    try:
        import prism

        contexto = prism.Context()
    except Exception:
        return SinScreenReader()
    return elegir_screen_reader(contexto)


class Voz:
    def __init__(self, screen_reader: ScreenReader | None = None) -> None:
        self._screen_reader = (
            screen_reader if screen_reader is not None else buscar_screen_reader()
        )

    def anunciar(self, texto: str) -> bool:
        """Lo dice sin cortar lo que el lector esté leyendo. Devuelve si llegó a
        decirse."""
        if not texto:
            return False
        try:
            self._screen_reader.decir(texto)
        except Exception:
            return False
        return True
