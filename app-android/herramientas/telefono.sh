# Lo comparten `instalar` y `probar-en-telefono`: el entorno y a qué teléfono hablar.
#
# adb no busca el teléfono en la red: con su propio descubrimiento (mDNS), la
# 37.0.1 se cae en este Mac al leer el nombre de algún servicio que anuncia
# otro aparato de la red (comprobado el 2026-10-15: SIGABRT en
# adbmdns_bridge::zero_config, según el informe de cuelgue de macOS). Por eso
# ADB_MDNS=0, y el teléfono se busca con dns-sd, el descubrimiento de macOS,
# para conectar después con su dirección y su puerto. El puerto cambia al
# reiniciar o al reactivar la depuración inalámbrica, así que no se guarda.
#
# El mismo teléfono puede salir dos veces en `adb devices`: por cable y por su
# dirección y puerto. Con más de uno, adb se niega a elegir, así que se elige
# aquí: primero la red y, si no hay, el cable. ANDROID_SERIAL=<serie> manda
# sobre todo esto.

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ADB_MDNS=0
ADB="$ANDROID_HOME/platform-tools/adb"

# dns-sd no termina nunca: escucha hasta que se le corta. Dos segundos bastan
# para que conteste un teléfono que está en la red. Lo corta perl y no un
# `alarm`, porque con `alarm` bash avisaba del corte con un «Alarm clock».
dns_sd() {
    perl -e '$p = fork; exec @ARGV unless $p;
        sleep 2; kill "TERM", $p; waitpid $p, 0' dns-sd "$@" 2>/dev/null || true
}

conectar_por_red() {
    local instancia destino servidor puerto ip
    instancia=$(dns_sd -B _adb-tls-connect._tcp |
        awk '$2 == "Add" { print $NF; exit }')
    [ -n "$instancia" ] || return 0
    destino=$(dns_sd -L "$instancia" _adb-tls-connect._tcp local. |
        sed -n 's/.* can be reached at \([^ ]*\):\([0-9]*\) .*/\1 \2/p' |
        head -1)
    [ -n "$destino" ] || return 0
    servidor=${destino% *}
    puerto=${destino#* }
    ip=$(dns_sd -G v4 "$servidor" | awk '$2 == "Add" { print $6; exit }')
    [ -n "$ip" ] || return 0
    "$ADB" connect "$ip:$puerto" >/dev/null || true
}

conectados() {
    "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

if [ -z "${ANDROID_SERIAL:-}" ]; then
    if ! conectados | grep -q ':'; then
        conectar_por_red
    fi
    ANDROID_SERIAL=$(
        { conectados | grep ':' || conectados; } | head -1
    )
fi

if [ -z "$ANDROID_SERIAL" ]; then
    echo "No hay ningún teléfono conectado. Esto es lo que ve adb:"
    "$ADB" devices -l
    exit 1
fi
export ANDROID_SERIAL
