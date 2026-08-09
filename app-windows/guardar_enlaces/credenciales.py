"""Guarda el token de refresco en el Administrador de credenciales de Windows
via `keyring`, nunca en un fichero plano. El token de ACCESO (corta duracion)
no se persiste aqui: solo vive en memoria mientras la app esta abierta.
"""

from __future__ import annotations

import keyring

_SERVICIO = "guardar-enlaces"
_USUARIO = "token-refresco"


def guardar_token_refresco(token: str) -> None:
    keyring.set_password(_SERVICIO, _USUARIO, token)


def obtener_token_refresco() -> str | None:
    return keyring.get_password(_SERVICIO, _USUARIO)


def borrar_token_refresco() -> None:
    try:
        keyring.delete_password(_SERVICIO, _USUARIO)
    except keyring.errors.PasswordDeleteError:
        pass  # no habia nada guardado: cerrar sesion sin haber iniciado sesion es un no-op
