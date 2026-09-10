"""Estado de sesion: token de acceso en memoria, token de refresco persistido
via credenciales.py (Administrador de credenciales de Windows).
"""

from __future__ import annotations

from typing import Callable, TypeVar

from . import credenciales
from .api_cliente import ClienteApi, ErrorApi

T = TypeVar("T")


class Sesion:
    def __init__(self, cliente: ClienteApi):
        self._cliente = cliente
        self.token_acceso: str | None = None
        self.usuario: dict | None = None

    @property
    def autenticado(self) -> bool:
        return self.token_acceso is not None

    def iniciar_con_codigo_canje(self, codigo_canje: str) -> None:
        """Ultimo paso del login con Google: cambia por tokens el codigo que
        trajo login_oauth.esperar_codigo_canje()."""
        self._aplicar_tokens(self._cliente.canjear(codigo_canje))

    def iniciar_con_dev_login(self, email: str) -> None:
        """SOLO sirve si el servidor tiene PERMITIR_LOGIN_DEV=true
        (ver ClienteApi.dev_login)."""
        self._aplicar_tokens(self._cliente.dev_login(email))

    def restaurar(self) -> bool:
        """Intenta recuperar la sesion con el token de refresco guardado de
        una vez anterior. Devuelve False si no habia, o si ya no sirve."""
        token_refresco = credenciales.obtener_token_refresco()
        if not token_refresco:
            return False
        try:
            self._aplicar_tokens(self._cliente.renovar(token_refresco))
        except ErrorApi:
            credenciales.borrar_token_refresco()
            return False
        return True

    def cerrar(self) -> None:
        token_refresco = credenciales.obtener_token_refresco()
        if token_refresco:
            try:
                self._cliente.logout(token_refresco)
            except ErrorApi:
                pass  # cerrar sesion localmente aunque el servidor no responda
        credenciales.borrar_token_refresco()
        self.token_acceso = None
        self.usuario = None

    def con_reintento(self, funcion: Callable[[str], T]) -> T:
        """Ejecuta funcion(token_acceso); si el servidor dice 401 (token de
        acceso caducado), renueva una vez con el token de refresco y
        reintenta. Si la renovacion tambien falla, se propaga el error
        (la interfaz debe volver a pedir inicio de sesion)."""
        try:
            return funcion(self.token_acceso)
        except ErrorApi as error:
            if error.status_code != 401 or not self.restaurar():
                raise
            return funcion(self.token_acceso)

    def _aplicar_tokens(self, datos: dict) -> None:
        self.token_acceso = datos["tokenAcceso"]
        if datos.get("usuario") is not None:
            self.usuario = datos["usuario"]
        if datos.get("tokenRefresco"):
            credenciales.guardar_token_refresco(datos["tokenRefresco"])
