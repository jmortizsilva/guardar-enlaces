# Lo comparten `instalar` y `probar-en-telefono`: el entorno y a qué teléfono hablar.
#
# El mismo teléfono puede salir hasta tres veces en `adb devices`: por cable,
# por su dirección y puerto, y por el nombre que anuncia en la red. Con más de
# uno, adb se niega a elegir, así que se elige aquí: primero el nombre de red,
# que no cambia aunque el teléfono cambie de puerto al reiniciar; después la
# dirección; y si no hay Wi-Fi, el cable. ANDROID_SERIAL=<serie> manda sobre
# todo esto.

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

if [ -z "${ANDROID_SERIAL:-}" ]; then
    conectados=$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    ANDROID_SERIAL=$(
        { echo "$conectados" | grep '_adb-tls-connect' ||
            echo "$conectados" | grep ':' ||
            echo "$conectados"; } | head -1
    )
fi

if [ -z "$ANDROID_SERIAL" ]; then
    echo "No hay ningún teléfono conectado. Esto es lo que ve adb:"
    "$ADB" devices -l
    exit 1
fi
export ANDROID_SERIAL
