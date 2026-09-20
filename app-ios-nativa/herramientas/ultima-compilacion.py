#!/usr/bin/env python3
"""El numero de la ultima compilacion subida a App Store Connect.

    ultima-compilacion.py <KeyID> <IssuerID> <bundleId>

Imprime 0 si la app todavia no tiene ninguna, que es lo que hace falta para
que la primera sea la 1.

Se pregunta en vez de llevar la cuenta en un fichero del repositorio porque la
cuenta buena es la de Apple: si una subida falla a medio camino, el numero ya
esta gastado igual, y un contador local se quedaria desfasado sin que nadie se
entere hasta que Apple rechace la siguiente.
"""

import json
import subprocess
import sys
from pathlib import Path


def token(key_id: str, issuer_id: str) -> str:
    """Se reutiliza el guion de al lado en vez de repetir la firma del JWT."""
    guion = Path(__file__).parent / "token-appstore.py"
    return subprocess.run(
        [sys.executable, str(guion), key_id, issuer_id],
        capture_output=True,
        check=True,
    ).stdout.decode().strip()


def main() -> None:
    if len(sys.argv) != 4:
        sys.exit("uso: ultima-compilacion.py <KeyID> <IssuerID> <bundleId>")
    key_id, issuer_id, bundle = sys.argv[1:]
    cabecera = f"Authorization: Bearer {token(key_id, issuer_id)}"
    base = "https://api.appstoreconnect.apple.com/v1"

    def pedir(ruta: str) -> dict:
        # -g porque los filtros de esta API llevan corchetes
        # (filter[bundleId]) y curl, sin el, los toma por comodines y ni
        # llega a pedir nada: sale con codigo 3 y sin explicar por que.
        return json.loads(
            subprocess.run(
                ["curl", "-sg", "-H", cabecera, base + ruta],
                capture_output=True,
                check=True,
            ).stdout
        )

    apps = pedir(f"/apps?filter[bundleId]={bundle}&limit=1").get("data", [])
    if not apps:
        # Sin ficha todavia. No es un error aqui: el fallo, si lo hay, sale
        # mas tarde y con un mensaje de Apple que se entiende mejor.
        print(0)
        return

    # Por /builds con filtro y NO por /apps/<id>/builds. Comprobado el
    # 2026-09-20 con la primera subida: la compilacion ya estaba VALID y
    # visible por aqui, mientras que la otra ruta seguia devolviendo una lista
    # vacia. Con ella, la siguiente subida habria repetido el numero 1 y Apple
    # la habria rechazado.
    builds = pedir(f"/builds?filter[app]={apps[0]['id']}&limit=1&sort=-version").get("data", [])
    if not builds:
        print(0)
        return
    print(int(builds[0]["attributes"]["version"]))


if __name__ == "__main__":
    main()
