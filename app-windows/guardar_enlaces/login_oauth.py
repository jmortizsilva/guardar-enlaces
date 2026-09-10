"""Login con Google por navegador del sistema, en el modo `polling` del
contrato (ver ../backend/docs/CONTRATO-API.md).

La app NUNCA habla con Google: abre `/auth/iniciar` del backend en el
navegador, el backend hace todo el intercambio OAuth y deja el resultado en un
buzon que esta funcion sondea. El codigo de canje es de un solo uso y dura
~60s; cambiarlo por tokens es cosa de Sesion, aqui solo se consigue.

Es el otro extremo del mismo flujo que en el iPhone resuelve
`app-ios/src/sesion/loginProveedor.ts`, con la diferencia que impone el
contrato: alli el movil recibe la vuelta por deep link, aqui no hay a donde
volver, asi que se pregunta al servidor cada segundo y medio.

Sin wx a proposito: todo lo de aqui corre en el hilo de fondo y se prueba sin
levantar interfaz.
"""

from __future__ import annotations

import secrets
import time
import webbrowser
from dataclasses import dataclass
from typing import Callable

from .api_cliente import ClienteApi, ErrorApi

INTERVALO_SONDEO_S = 1.5
# El login pendiente caduca a los 5 minutos en el servidor
# (DURACION_LOGIN_PENDIENTE_MS): no tiene sentido seguir preguntando despues.
LIMITE_ESPERA_S = 5 * 60
# Un corte de red puntual no debe tirar el intento entero, pero si el servidor
# no responde varias veces seguidas es que no va a responder.
MAXIMO_FALLOS_SEGUIDOS = 5


@dataclass(frozen=True)
class ResultadoLogin:
    """`estado` es "exito", "cancelado" o "error"."""

    estado: str
    codigo_canje: str | None = None
    mensaje: str | None = None


def generar_estado() -> str:
    """El "estado" del contrato: cadena opaca de un solo uso que ata la vuelta
    del navegador a esta peticion concreta. 16 bytes en hexadecimal."""
    return secrets.token_hex(16)


def mensaje_de_error(motivo: str) -> str:
    """Traduce los motivos de error del contrato a algo que se pueda leer en
    voz alta."""
    if motivo == "sin_email":
        return "Tu cuenta no ha dado ningún correo, y hace falta para crear la cuenta."
    if motivo == "fallo_intercambio":
        return "Google rechazó el inicio de sesión. Vuelve a intentarlo."
    return "No se pudo iniciar sesión."


def abrir_navegador(url: str) -> bool:
    """True si se pudo abrir el navegador del sistema."""
    return webbrowser.open(url)


def esperar_codigo_canje(
    cliente: ClienteApi,
    estado: str,
    cancelado: Callable[[], bool],
    dormir: Callable[[float], None] = time.sleep,
    reloj: Callable[[], float] = time.monotonic,
    intervalo_s: float = INTERVALO_SONDEO_S,
    limite_s: float = LIMITE_ESPERA_S,
) -> ResultadoLogin:
    """Pregunta a /auth/estado hasta que el usuario termina en el navegador.

    `cancelado()` se consulta en cada vuelta para poder abandonar en cuanto se
    cierra el dialogo, sin esperar a que se agote el tiempo. `dormir` y `reloj`
    se inyectan para que las pruebas no tarden cinco minutos.
    """
    inicio = reloj()
    fallos_seguidos = 0

    while reloj() - inicio < limite_s:
        if cancelado():
            return ResultadoLogin("cancelado")

        try:
            respuesta = cliente.estado_login(estado)
        except ErrorApi as error:
            if error.status_code == 404:
                # El buzon ya no existe: caducado, o ya se consumio el codigo.
                return ResultadoLogin(
                    "error",
                    mensaje="El inicio de sesión caducó. Vuelve a intentarlo.",
                )
            fallos_seguidos += 1
            if fallos_seguidos >= MAXIMO_FALLOS_SEGUIDOS:
                return ResultadoLogin(
                    "error", mensaje=f"No se pudo hablar con el servidor: {error}"
                )
        else:
            fallos_seguidos = 0
            if respuesta.get("listo"):
                codigo_canje = respuesta.get("codigoCanje")
                if codigo_canje:
                    return ResultadoLogin("exito", codigo_canje=codigo_canje)
                return ResultadoLogin(
                    "error", mensaje=mensaje_de_error(respuesta.get("error", ""))
                )

        dormir(intervalo_s)

    return ResultadoLogin(
        "error",
        mensaje="Se acabó el tiempo de espera. Vuelve a intentarlo cuando puedas "
        "terminar en el navegador.",
    )
