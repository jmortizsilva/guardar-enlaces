#!/bin/bash
# Escribe el identificador del simulador propio de Guardalo, y lo crea si no
# existe todavia.
#
# Propio y no el "iPhone 17" de todos porque en este Mac hay mas proyectos que
# pasan pruebas de interfaz (el 2026-09-29, InventarioCasa a la vez que este).
# Contra el mismo simulador se pisaban: el Mac a carga 30, las pruebas tardando
# quince minutos en vez de tres, y tres que fallaban sin tener nada roto.
# Con uno para cada proyecto, los dos pueden probar a la vez.
set -euo pipefail

NOMBRE="Guardalo iPhone 17"
TIPO="com.apple.CoreSimulator.SimDeviceType.iPhone-17"
SISTEMA="com.apple.CoreSimulator.SimRuntime.iOS-27-0"

existente=$(xcrun simctl list devices -j | python3 -c "
import json, sys
nombre = sys.argv[1]
for sistema, aparatos in json.load(sys.stdin)['devices'].items():
    for a in aparatos:
        if a['name'] == nombre and a.get('isAvailable'):
            print(a['udid'])
            sys.exit()
" "$NOMBRE")

if [ -n "$existente" ]; then
    echo "$existente"
else
    xcrun simctl create "$NOMBRE" "$TIPO" "$SISTEMA"
fi
