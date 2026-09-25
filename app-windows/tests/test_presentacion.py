from guardar_enlaces.modelo import nuevo_elemento_local
from guardar_enlaces.presentacion import (
    texto_detalle,
    texto_fila,
    texto_resultado_importacion,
)


def test_incluye_titulo_dominio_etiquetas_y_fecha():
    e = nuevo_elemento_local(
        "https://www.ejemplo.com/articulo",
        titulo="Un articulo interesante",
        etiquetas=("ocio", "pendiente"),
        ahora=lambda: 1_700_000_000_000,
    )
    texto = texto_fila(e)
    assert texto.startswith("Un articulo interesante — www.ejemplo.com — ocio, pendiente — ")


def test_sin_titulo_usa_la_url():
    e = nuevo_elemento_local("https://a.com", ahora=lambda: 1_700_000_000_000)
    assert texto_fila(e).startswith("https://a.com — a.com")


def test_sin_etiquetas_no_deja_un_separador_vacio():
    e = nuevo_elemento_local("https://a.com", titulo="A", ahora=lambda: 1_700_000_000_000)
    assert " —  — " not in texto_fila(e)


def test_detalle_una_linea_por_dato_y_sin_lineas_vacias():
    completo = nuevo_elemento_local("https://a.com", titulo="A", descripcion="Una descripcion")
    assert texto_detalle(completo) == "A\nhttps://a.com\nUna descripcion"
    assert texto_detalle(nuevo_elemento_local("https://a.com")) == "https://a.com"


# --- Lo que se oye al importar --------------------------------------------


def test_resultado_importacion_concuerda_los_plurales():
    """Nunca «enlace(s)»: si el numero manda, la frase entera cambia con el."""
    assert texto_resultado_importacion(143, 12) == "Importados 143 enlaces. 12 ya los tenías."
    assert texto_resultado_importacion(143, 0) == "Importados 143 enlaces."
    assert texto_resultado_importacion(1, 0) == "Importado 1 enlace."
    assert texto_resultado_importacion(1, 1) == "Importado 1 enlace. Uno ya lo tenías."
    assert texto_resultado_importacion(2, 1) == "Importados 2 enlaces. Uno ya lo tenías."


def test_resultado_importacion_cuando_no_entra_nada_dice_por_que():
    """Las dos razones de que no entre nada son distintas y hay que
    distinguirlas: no es lo mismo un fichero sin direcciones que un fichero
    cuyos enlaces ya tienes."""
    assert (
        texto_resultado_importacion(0, 12)
        == "No hay nada nuevo: los 12 enlaces del fichero ya los tenías."
    )
    assert (
        texto_resultado_importacion(0, 1)
        == "No hay nada nuevo: el único enlace del fichero ya lo tenías."
    )
    assert texto_resultado_importacion(0, 0) == "Ese fichero no tiene ninguna dirección."
