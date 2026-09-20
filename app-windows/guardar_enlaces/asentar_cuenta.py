"""Que hacer con lo que ya hay en este ordenador cuando alguien entra en una
cuenta. Logica pura sobre el almacen: se prueba con un almacen falso, sin
SQLite ni wx.

Calco de app-ios-nativa/Dominio/Fuentes/Dominio/AsentarCuenta.swift.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Protocol


class AlmacenAsentable(Protocol):
    """Lo que asentar_cuenta necesita del almacen; AlmacenLocal lo cumple."""

    def dueno_actual(self) -> str | None: ...
    def fijar_dueno(self, valor: str) -> None: ...
    def contar_elementos(self) -> int: ...
    def adoptar_con_ids_nuevos(self) -> None: ...
    def vaciar(self) -> None: ...
    def fijar_cursor(self, valor: int) -> None: ...


@dataclass(frozen=True)
class EnlacesEnElEquipo:
    cuantos: int
    #: True si son de otra cuenta; False si nunca hubo cuenta.
    de_otra_cuenta: bool


#: Lo pregunta la interfaz: la decision es del usuario, no de aqui.
DecidirImportacion = Callable[[EnlacesEnElEquipo], bool]


def identidad_dueno(url_servidor: str, email: str) -> str:
    """El "dueno" de la cache. Lleva la URL del servidor ademas del correo: el
    mismo correo en el servidor de pruebas y en el real son dos bibliotecas
    distintas, y mezclarlas seria peor que empezar de cero."""
    return f"{url_servidor}|{email}"


def asentar_cuenta(
    almacen: AlmacenAsentable,
    dueno: str,
    decidir_importacion: DecidirImportacion,
) -> None:
    """La cache local NO esta separada por cuenta, asi que al entrar hay que
    resolver de quien es lo que hay: o se importa a la cuenta nueva, o se borra.
    Se pregunta en vez de decidirlo aqui, porque las dos respuestas son
    razonables y ninguna se puede deshacer.

    Entrar en la cuenta de siempre no pregunta ni toca nada, que es el caso
    normal; la pregunta solo aparece al estrenar cuenta o al cambiar de una a
    otra, y solo si hay algo que decidir.
    """
    dueno_anterior = almacen.dueno_actual()
    if dueno_anterior == dueno:
        return

    cuantos = almacen.contar_elementos()
    importar = cuantos > 0 and decidir_importacion(
        EnlacesEnElEquipo(cuantos=cuantos, de_otra_cuenta=dueno_anterior is not None)
    )

    if importar:
        almacen.adoptar_con_ids_nuevos()
    else:
        almacen.vaciar()
    # El cursor era del OTRO servidor: si no se pone a cero, el primer pull pide
    # "lo cambiado desde" una fecha que en este no significa nada, y se salta
    # todo lo anterior a ella. vaciar() ya lo borra; esto cubre el otro camino.
    almacen.fijar_cursor(0)
    almacen.fijar_dueno(dueno)
