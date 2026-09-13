"""Cliente HTTP del backend. Ver el contrato completo en
../backend/docs/CONTRATO-API.md.

Fontaneria pura: no decide nada, solo traduce llamadas Python a peticiones
HTTP y errores HTTP a excepciones con mensaje en castellano. Se llama siempre
desde el hilo de fondo (nunca desde el hilo de la interfaz de wx).
"""

from __future__ import annotations

from urllib.parse import urlencode

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

    def url_iniciar_login(self, proveedor: str, estado: str) -> str:
        """URL de arranque del login OAuth. No se pide desde aqui: se abre en
        el navegador del sistema (ver login_oauth.py). El servidor responde un
        302 al consentimiento del proveedor."""
        parametros = urlencode(
            {"proveedor": proveedor, "modo": "polling", "estado": estado}
        )
        return f"{self.url_base}/auth/iniciar?{parametros}"

    def estado_login(self, estado: str) -> dict:
        """Sondeo del buzon del login: {"listo": false} mientras el usuario
        sigue en el navegador."""
        return self._get("/auth/estado", parametros={"estado": estado})

    def canjear(self, codigo_canje: str) -> dict:
        """Cambia el codigo de canje (un solo uso, ~60s de vida) por tokens."""
        return self._post("/auth/canjear", cuerpo={"codigoCanje": codigo_canje})

    def dev_login(self, email: str) -> dict:
        """SOLO sirve si el servidor tiene PERMITIR_LOGIN_DEV=true. Ya no hay
        pantalla que lo use (el login es Google): queda como unica forma de
        entrar contra un servidor de pruebas sin credenciales OAuth reales."""
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

    def push(
        self,
        elementos: list[dict] | None = None,
        token_acceso: str = "",
        etiquetas_definidas: list[dict] | None = None,
    ) -> dict:
        cuerpo: dict = {}
        if elementos:
            cuerpo["elementos"] = elementos
        if etiquetas_definidas:
            cuerpo["etiquetasDefinidas"] = etiquetas_definidas
        return self._post("/sincronizar", cuerpo=cuerpo, token_acceso=token_acceso)

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
            # Sin el texto de requests: se lee en voz alta y es ilegible. Queda
            # en la excepcion encadenada para diagnosticar.
            raise ErrorApi("sin conexión con el servidor") from error
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
            # Sin el texto de requests: se lee en voz alta y es ilegible. Queda
            # en la excepcion encadenada para diagnosticar.
            raise ErrorApi("sin conexión con el servidor") from error
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
