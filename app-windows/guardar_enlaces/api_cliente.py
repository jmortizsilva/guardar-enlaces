"""Cliente HTTP del backend (servidor-guardar-enlaces). Ver el contrato
completo en docs/CONTRATO-API.md, en el repo del servidor.

Fontaneria pura: no decide nada, solo traduce llamadas Python a peticiones
HTTP y errores HTTP a excepciones con mensaje en castellano. Se llama siempre
desde el hilo de fondo (nunca desde el hilo de la interfaz de wx).
"""

from __future__ import annotations

import requests


class ErrorApi(Exception):
    def __init__(self, mensaje: str, status_code: int | None = None):
        super().__init__(mensaje)
        self.status_code = status_code


class ClienteApi:
    def __init__(self, url_base: str, timeout: float = 10.0):
        self.url_base = url_base.rstrip("/")
        self.timeout = timeout

    # --- autenticacion ---

    def dev_login(self, email: str) -> dict:
        """SOLO sirve si el servidor tiene PERMITIR_LOGIN_DEV=true."""
        return self._post("/auth/dev-login", cuerpo={"email": email})

    def renovar(self, token_refresco: str) -> dict:
        return self._post("/auth/renovar", cuerpo={"tokenRefresco": token_refresco})

    def logout(self, token_refresco: str) -> None:
        self._post("/auth/logout", cuerpo={"tokenRefresco": token_refresco})

    # --- metadatos ---

    def metadatos(self, url: str, token_acceso: str) -> dict:
        return self._post("/metadatos", cuerpo={"url": url}, token_acceso=token_acceso)

    # --- sincronizacion ---

    def pull(self, desde: int, token_acceso: str, limite: int = 300) -> dict:
        return self._get(
            "/sincronizar",
            parametros={"desde": desde, "limite": limite},
            token_acceso=token_acceso,
        )

    def push(self, elementos: list[dict], token_acceso: str) -> dict:
        return self._post(
            "/sincronizar", cuerpo={"elementos": elementos}, token_acceso=token_acceso
        )

    # --- internals ---

    def _cabeceras(self, token_acceso: str | None) -> dict:
        return {"Authorization": f"Bearer {token_acceso}"} if token_acceso else {}

    def _post(self, ruta: str, cuerpo: dict, token_acceso: str | None = None) -> dict:
        try:
            respuesta = requests.post(
                f"{self.url_base}{ruta}",
                json=cuerpo,
                headers=self._cabeceras(token_acceso),
                timeout=self.timeout,
            )
        except requests.RequestException as error:
            raise ErrorApi(f"no se pudo conectar con el servidor: {error}") from error
        return _procesar_respuesta(respuesta)

    def _get(self, ruta: str, parametros: dict, token_acceso: str | None = None) -> dict:
        try:
            respuesta = requests.get(
                f"{self.url_base}{ruta}",
                params=parametros,
                headers=self._cabeceras(token_acceso),
                timeout=self.timeout,
            )
        except requests.RequestException as error:
            raise ErrorApi(f"no se pudo conectar con el servidor: {error}") from error
        return _procesar_respuesta(respuesta)


def _procesar_respuesta(respuesta: requests.Response) -> dict:
    if respuesta.status_code >= 400:
        raise ErrorApi(_mensaje_de_error(respuesta), status_code=respuesta.status_code)
    if not respuesta.content:
        return {}
    return respuesta.json()


def _mensaje_de_error(respuesta: requests.Response) -> str:
    try:
        return respuesta.json().get("error", f"error {respuesta.status_code}")
    except ValueError:
        return f"error {respuesta.status_code}"
