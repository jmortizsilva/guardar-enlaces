"""Punto de entrada para PyInstaller.

Existe porque PyInstaller necesita un script suelto, no un paquete: apuntarle a
`guardar_enlaces/__main__.py` funciona a veces y a veces rompe los imports
relativos, segun como resuelva el paquete. Un fichero de dos lineas fuera del
paquete no tiene esa duda.

Para ejecutar a mano se sigue usando `python -m guardar_enlaces`.
"""

from guardar_enlaces.main import main

if __name__ == "__main__":
    main()
