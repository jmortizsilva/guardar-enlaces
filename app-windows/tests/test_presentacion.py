from guardar_enlaces.modelo import nuevo_elemento_local
from guardar_enlaces.presentacion import texto_detalle, texto_fila


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
