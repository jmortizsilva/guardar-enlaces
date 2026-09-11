"""Genera el icono de la aplicacion: un marcador de libro blanco sobre fondo
azul, en un .ico con todos los tamanos que pide Windows.

Se dibuja por codigo en vez de guardar un binario en el repositorio: asi el
icono se puede cambiar leyendo cuatro numeros, y en una revision se ve que
cambia de verdad (un .ico no se puede diffear).

Uso:  python recursos/generar_icono.py

Por que un marcador: a 16 pixeles, que es como se ve en la barra de tareas, las
figuras con huecos finos (un eslabon de cadena, un clip) se emborronan hasta ser
una mancha. Una silueta maciza con una muesca grande sigue siendo reconocible.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

# Tamanos que Windows usa segun el sitio: lista pequena, barra de tareas,
# escritorio, y el grande de las vistas de iconos y la tienda.
TAMANOS = [16, 24, 32, 48, 64, 128, 256]

AZUL = (21, 87, 172, 255)
BLANCO = (255, 255, 255, 255)

DESTINO = Path(__file__).parent / "guardar-enlaces.ico"


def dibujar(lado: int) -> Image.Image:
    """El icono a un tamano concreto. Se dibuja a 8x y se reduce al final, que
    es lo que le quita los dientes de sierra a las diagonales de la muesca."""
    escala = 8
    grande = lado * escala
    imagen = Image.new("RGBA", (grande, grande), (0, 0, 0, 0))
    lienzo = ImageDraw.Draw(imagen)

    # Fondo: cuadrado de esquinas redondeadas, casi a sangre.
    margen = grande * 0.04
    lienzo.rounded_rectangle(
        [margen, margen, grande - margen, grande - margen],
        radius=grande * 0.18,
        fill=AZUL,
    )

    # El marcador: un rectangulo alto y centrado.
    ancho = grande * 0.40
    izquierda = (grande - ancho) / 2
    derecha = izquierda + ancho
    arriba = grande * 0.20
    abajo = grande * 0.78
    # La muesca sube hasta aqui desde el borde inferior: cuanto mas profunda,
    # mejor se lee a 16 pixeles.
    vertice = abajo - grande * 0.22

    lienzo.polygon(
        [
            (izquierda, arriba),
            (derecha, arriba),
            (derecha, abajo),
            ((izquierda + derecha) / 2, vertice),
            (izquierda, abajo),
        ],
        fill=BLANCO,
    )

    return imagen.resize((lado, lado), Image.LANCZOS)


def main() -> None:
    imagenes = [dibujar(lado) for lado in TAMANOS]
    imagenes[-1].save(DESTINO, format="ICO", sizes=[(t, t) for t in TAMANOS])
    print(f"icono escrito en {DESTINO} ({', '.join(str(t) for t in TAMANOS)} px)")


if __name__ == "__main__":
    main()
