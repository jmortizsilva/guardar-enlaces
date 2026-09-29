"""El lector de ficheros exportados, contra los ejemplos compartidos.

Estas pruebas NO traen sus propios casos: leen los mismos ficheros y el mismo
`esperado.json` que las de iOS y Android, en `pruebas-compartidas/importacion/`.
Ese es el unico sitio donde se decide que debe salir de cada fichero; si
alguien cambia el comportamiento de un cliente sin pasar por ahi, esta prueba
falla.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from guardar_enlaces.importar import CSV, HTML, TXT, detectar_formato, leer, preparar
from guardar_enlaces.modelo import nuevo_elemento_local

COMPARTIDAS = Path(__file__).resolve().parents[2] / "pruebas-compartidas" / "importacion"


def _esperado() -> dict:
    return json.loads((COMPARTIDAS / "esperado.json").read_text(encoding="utf-8"))


def _contenido(nombre: str) -> str:
    return (COMPARTIDAS / nombre).read_text(encoding="utf-8")


FICHEROS = ["marcadores-navegador.html", "pocket.csv", "raindrop.csv", "excel.csv", "enlaces.txt"]


def test_los_ficheros_compartidos_estan_donde_se_espera():
    """Si esto falla, es que se movio la carpeta compartida y las otras dos
    apps tambien se han quedado sin sus casos."""
    assert COMPARTIDAS.is_dir(), f"no esta {COMPARTIDAS}"
    for nombre in [*FICHEROS, "esperado.json"]:
        assert (COMPARTIDAS / nombre).is_file(), nombre


@pytest.mark.parametrize("nombre", FICHEROS)
def test_sale_exactamente_lo_que_dice_el_contrato(nombre):
    esperado = _esperado()[nombre]
    lectura = leer(_contenido(nombre))

    assert [e.url for e in lectura.enlaces] == [e["url"] for e in esperado["elementos"]]
    assert [e.titulo for e in lectura.enlaces] == [e["titulo"] for e in esperado["elementos"]]
    assert [list(e.etiquetas) for e in lectura.enlaces] == [
        e["etiquetas"] for e in esperado["elementos"]
    ]
    assert [e.creado_en for e in lectura.enlaces] == [
        e["creadoEn"] for e in esperado["elementos"]
    ]
    assert lectura.descartados == esperado["descartados"]


def test_el_formato_se_reconoce_por_el_contenido_no_por_el_nombre():
    assert detectar_formato(_contenido("marcadores-navegador.html")) == HTML
    assert detectar_formato(_contenido("pocket.csv")) == CSV
    assert detectar_formato(_contenido("raindrop.csv")) == CSV
    assert detectar_formato(_contenido("excel.csv")) == CSV
    assert detectar_formato(_contenido("enlaces.txt")) == TXT


def test_un_csv_sin_columna_de_direccion_no_importa_nada():
    """Sin direcciones no hay nada que traer, y no es un error del programa."""
    lectura = leer("nombre,comentario\nAlgo,Otra cosa\n")
    assert lectura.enlaces == ()


def test_un_fichero_vacio_no_revienta():
    lectura = leer("")
    assert lectura.enlaces == ()
    assert lectura.descartados == 0


def test_los_repetidos_del_propio_fichero_se_cuentan_una_vez():
    """Dos direcciones que solo cambian en los parametros de seguimiento son
    la misma, igual que en el resto de la aplicacion."""
    lectura = leer(
        "https://ejemplo.com/a?utm_source=twitter\nhttps://ejemplo.com/a\n"
    )
    assert len(lectura.enlaces) == 1
    assert lectura.descartados == 1


# --- Que se guarda de lo leido --------------------------------------------


def _reloj(valor: int = 9_000_000):
    return lambda: valor


def test_sin_nada_guardado_entran_todos():
    preparado = preparar(leer(_contenido("marcadores-navegador.html")), [], ahora=_reloj())
    assert preparado.importados == 6
    assert preparado.ya_estaban == 0


def test_un_enlace_que_ya_tienes_no_se_toca():
    """Lo contrario de guardar a mano, y a proposito: importar toca cientos de
    golpe, asi que pisar lo que pusiste tu seria un destrozo sin vuelta."""
    mio = nuevo_elemento_local(
        url="https://ejemplo.com/flan",
        titulo="Mi flan de siempre",
        etiquetas=("pendiente",),
        ahora=_reloj(1),
    )
    preparado = preparar(leer(_contenido("marcadores-navegador.html")), [mio], ahora=_reloj())

    assert preparado.ya_estaban == 1
    assert all(e.url != mio.url for e in preparado.nuevos)
    assert mio.titulo == "Mi flan de siempre"
    assert mio.etiquetas == ("pendiente",)


def test_la_fecha_del_fichero_manda_sobre_la_del_reloj():
    """Si no, los ochocientos llegan con la fecha de hoy y la lista deja de
    tener orden."""
    preparado = preparar(leer(_contenido("marcadores-navegador.html")), [], ahora=_reloj())
    porfecha = {e.titulo: e.creado_en for e in preparado.nuevos}

    assert porfecha["Arroz caldoso"] == 1700000300000
    # El unico del fichero sin ADD_DATE se queda con la de la importacion.
    assert porfecha["Un marcador sin ADD_DATE"] == 9_000_000


def test_el_mismo_enlace_dos_veces_en_la_misma_importacion_entra_una():
    preparado = preparar(leer("https://ejemplo.com/a\nhttps://ejemplo.com/a\n"), [], ahora=_reloj())
    assert preparado.importados == 1
