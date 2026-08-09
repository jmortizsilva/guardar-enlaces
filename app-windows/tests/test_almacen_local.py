import pytest

from guardar_enlaces.almacen_local import AlmacenLocal
from guardar_enlaces.modelo import editar, marcar_borrado, nuevo_elemento_local


@pytest.fixture
def almacen():
    a = AlmacenLocal(":memory:")
    yield a
    a.cerrar()


def test_guardar_y_cargar_todos(almacen):
    e = nuevo_elemento_local("https://a.com", titulo="A", etiquetas=("x", "y"), ahora=lambda: 100)
    almacen.guardar({e.id: e})

    cargados = almacen.cargar_todos()
    assert cargados == {e.id: e}


def test_marcar_pendiente_lo_deja_visible_y_en_el_outbox(almacen):
    e = nuevo_elemento_local("https://a.com", ahora=lambda: 100)
    almacen.marcar_pendiente(e)

    assert almacen.cargar_todos() == {e.id: e}
    assert almacen.cargar_pendientes() == {e.id: e}


def test_limpiar_pendientes_los_saca_del_outbox_pero_no_de_la_cache(almacen):
    e = nuevo_elemento_local("https://a.com", ahora=lambda: 100)
    almacen.marcar_pendiente(e)
    almacen.limpiar_pendientes([e.id])

    assert almacen.cargar_pendientes() == {}
    assert almacen.cargar_todos() == {e.id: e}


def test_guardar_sobre_un_elemento_existente_lo_actualiza(almacen):
    e = nuevo_elemento_local("https://a.com", titulo="Viejo", ahora=lambda: 100)
    almacen.guardar({e.id: e})

    editado = editar(e, ahora=lambda: 200, titulo="Nuevo")
    almacen.guardar({editado.id: editado})

    assert almacen.cargar_todos() == {editado.id: editado}


def test_cursor_por_defecto_es_cero_y_se_puede_fijar(almacen):
    assert almacen.cursor() == 0
    almacen.fijar_cursor(12345)
    assert almacen.cursor() == 12345
    almacen.fijar_cursor(99999)
    assert almacen.cursor() == 99999


def test_un_elemento_borrado_se_guarda_igual_con_su_tombstone(almacen):
    e = nuevo_elemento_local("https://a.com", ahora=lambda: 100)
    borrado = marcar_borrado(e, ahora=lambda: 200)
    almacen.guardar({borrado.id: borrado})

    assert almacen.cargar_todos()[e.id].borrado is True
