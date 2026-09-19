#!/usr/bin/env python3
"""Un token para la API de App Store Connect, sin instalar nada.

    TOKEN=$(herramientas/token-appstore.py 2V5QGQLFXK f313d445-…)
    curl -H "Authorization: Bearer $TOKEN" https://api.appstoreconnect.apple.com/v1/bundleIds

La clave se busca en ~/.appstoreconnect/private_keys/AuthKey_<KeyID>.p8, que
es donde la espera también xcodebuild. Nunca en el repositorio.

Se firma con openssl porque en este Mac no hay PyJWT ni cryptography, y para
esto no compensa instalarlos. El detalle que se lleva el rato: `openssl dgst
-sign` devuelve la firma en DER (una secuencia con dos enteros de longitud
variable), y ES256 la quiere como los dos enteros crudos de 32 bytes pegados.
Sin convertirla, Apple contesta 401 sin decir por qué.
"""

import base64
import json
import os
import subprocess
import sys
import time


def b64(datos: bytes) -> bytes:
    """Base64 de URL y sin relleno, que es lo que usa JWT."""
    return base64.urlsafe_b64encode(datos).rstrip(b"=")


def entero_de_der(datos: bytes, posicion: int) -> tuple[bytes, int]:
    assert datos[posicion] == 0x02, "se esperaba un entero DER"
    largo = datos[posicion + 1]
    valor = datos[posicion + 2 : posicion + 2 + largo]
    # DER mete un cero delante si el primer bit es 1, para que no se lea
    # como negativo; y omite los ceros a la izquierda. ES256 quiere 32
    # bytes exactos en los dos casos.
    return valor.lstrip(b"\x00").rjust(32, b"\x00"), posicion + 2 + largo


def token(key_id: str, issuer_id: str) -> str:
    ruta = os.path.expanduser(f"~/.appstoreconnect/private_keys/AuthKey_{key_id}.p8")
    ahora = int(time.time())
    cabecera = {"alg": "ES256", "kid": key_id, "typ": "JWT"}
    cuerpo = {
        "iss": issuer_id,
        "iat": ahora,
        # Apple rechaza los que duren más de 20 minutos.
        "exp": ahora + 600,
        "aud": "appstoreconnect-v1",
    }
    sin_firma = b64(json.dumps(cabecera).encode()) + b"." + b64(json.dumps(cuerpo).encode())

    der = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", ruta],
        input=sin_firma,
        capture_output=True,
        check=True,
    ).stdout

    assert der[0] == 0x30, "se esperaba una secuencia DER"
    posicion = 2 if der[1] < 0x80 else 3
    r, posicion = entero_de_der(der, posicion)
    s, _ = entero_de_der(der, posicion)
    return (sin_firma + b"." + b64(r + s)).decode()


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("uso: token-appstore.py <KeyID> <IssuerID>")
    print(token(sys.argv[1], sys.argv[2]))
