"""La direccion que se escribe a mano al anadir un enlace: completarle el
esquema si no lo trae. Ver ANADIR.md en la raiz del repositorio; los casos
estan en pruebas-compartidas/direcciones/ y los leen las tres apps.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable

ESQUEMAS = ("https://", "http://")


@dataclass(frozen=True)
class DireccionEscrita:
    direccion: str
    # La misma con http://, para probarla si la de https:// no carga. Solo
    # cuando el esquema lo ha puesto la app: si lo escribio quien la usa, se
    # respeta.
    alternativa: str | None


def completar(texto: str) -> DireccionEscrita | None:
    """La direccion lista para guardar, o None si lo escrito no lo es."""
    limpio = texto.strip()
    minusculas = limpio.lower()
    for esquema in ESQUEMAS:
        if minusculas.startswith(esquema):
            if len(limpio) > len(esquema):
                return DireccionEscrita(limpio, None)
            return None
    if not _parece_un_sitio(limpio):
        return None
    return DireccionEscrita(f"https://{limpio}", f"http://{limpio}")


def _parece_un_sitio(texto: str) -> bool:
    if not texto or any(letra.isspace() for letra in texto) or "://" in texto:
        return False
    sitio = texto
    for corte in "/?#":
        sitio = sitio.split(corte, 1)[0]
    # Un correo con https:// delante abriria el sitio de detras de la arroba.
    if "@" in sitio:
        return False
    punto = sitio.find(".")
    return 0 < punto < len(sitio) - 1


# --- Comprobar que carga (ANADIR.md) --------------------------------------

CARGA = "carga"
NO_CARGA = "no_carga"
# No se ha podido preguntar: sin red, o el servidor no contesta. Se guarda sin
# preguntar.
SIN_COMPROBAR = "sin_comprobar"


@dataclass(frozen=True)
class Comprobacion:
    estado: str
    # Solo si carga; vacios si la pagina no tiene titulo, que tambien carga.
    metadatos: dict = field(default_factory=dict)


@dataclass(frozen=True)
class DireccionComprobada:
    direccion: str
    comprobacion: Comprobacion


def comprobar_direccion(
    escrita: DireccionEscrita, comprobar: Callable[[str], Comprobacion]
) -> DireccionComprobada:
    """Prueba la direccion y, si no carga y el https:// lo puso la app, la
    alternativa con http://: algunas paginas antiguas solo responden asi. Se
    queda con la que cargue; si no carga ninguna, con la de https://, que es
    la que se ofrecera guardar igualmente."""
    primera = comprobar(escrita.direccion)
    if primera.estado != NO_CARGA or escrita.alternativa is None:
        return DireccionComprobada(escrita.direccion, primera)
    segunda = comprobar(escrita.alternativa)
    if segunda.estado == CARGA:
        return DireccionComprobada(escrita.alternativa, segunda)
    return DireccionComprobada(escrita.direccion, primera)
