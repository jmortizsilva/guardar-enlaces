"""La direccion que se escribe a mano al anadir un enlace: completarle el
esquema si no lo trae. Ver ANADIR.md en la raiz del repositorio; los casos
estan en pruebas-compartidas/direcciones/ y los leen las tres apps.
"""

from __future__ import annotations

from dataclasses import dataclass

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
