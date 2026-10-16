#!/bin/bash
# Crea, una sola vez, la clave de subida a Google Play (ver PLAN.md, fase 5).
#
# La contraseña se genera al azar y va directa al llavero de firma, el mismo
# que usa iOS (app-ios-nativa/docs/FIRMA-SIN-PANTALLA.md): por SSH, el llavero
# de inicio de sesión no se puede abrir. No sale por pantalla ni a un fichero.
#
# Si la clave ya existe no hace nada: crear otra encima dejaría sin poder
# subir versiones nuevas hasta que Google aceptara la nueva.
set -euo pipefail

ALMACEN="$HOME/.android/guardalo-subida.jks"
SERVICIO=guardalo-subida-android
LLAVERO=guardalo-firma.keychain
KEYTOOL=/opt/homebrew/opt/openjdk@21/bin/keytool

if [ -e "$ALMACEN" ]; then
    echo "Ya existe $ALMACEN. No se toca." >&2
    exit 1
fi

security unlock-keychain -p "$(cat ~/.appstoreconnect/llavero-firma.txt)" "$LLAVERO"

if ! security find-generic-password -s "$SERVICIO" "$LLAVERO" >/dev/null 2>&1; then
    security add-generic-password -a "$USER" -s "$SERVICIO" \
        -l "Guárdalo: clave de subida a Google Play" \
        -w "$(openssl rand -base64 33 | tr -d '/+=' | cut -c1-40)" "$LLAVERO"
fi
CLAVE_FIRMA="$(security find-generic-password -s "$SERVICIO" -w "$LLAVERO")"
export CLAVE_FIRMA

# PKCS12 usa la misma contraseña para el almacén y para la clave. 30 años:
# Google Play pide que dure al menos hasta 2033.
"$KEYTOOL" -genkeypair -keystore "$ALMACEN" -storetype PKCS12 \
    -alias subida -keyalg RSA -keysize 4096 -validity 10950 \
    -dname "CN=Guardalo, O=jmortizsilva" \
    -storepass:env CLAVE_FIRMA -keypass:env CLAVE_FIRMA 2>/dev/null
chmod 600 "$ALMACEN"

echo "Clave creada en $ALMACEN."
echo "Huella SHA-256 del certificado:"
"$KEYTOOL" -list -v -keystore "$ALMACEN" -storepass:env CLAVE_FIRMA \
    | grep -m1 "SHA256:" | sed 's/^[[:space:]]*//'
