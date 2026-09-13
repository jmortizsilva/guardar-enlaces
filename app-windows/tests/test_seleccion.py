from guardar_enlaces.seleccion import fila_tras_refrescar


def test_lista_vacia_no_tiene_fila():
    assert fila_tras_refrescar(["a"], 0, []) == -1


def test_primer_llenado_deja_activa_la_primera():
    assert fila_tras_refrescar([], -1, ["a", "b"]) == 0


def test_sin_fila_seleccionada_antes_tambien_queda_la_primera():
    assert fila_tras_refrescar(["a", "b"], -1, ["a", "b"]) == 0


def test_sigue_al_mismo_enlace_aunque_cambie_de_sitio():
    assert fila_tras_refrescar(["a", "b", "c"], 1, ["c", "a", "b"]) == 2


def test_si_el_enlace_ya_no_esta_se_queda_en_la_misma_posicion():
    assert fila_tras_refrescar(["a", "b", "c"], 1, ["a", "c"]) == 1


def test_si_la_lista_encoge_por_debajo_se_queda_en_la_ultima():
    assert fila_tras_refrescar(["a", "b", "c"], 2, ["a"]) == 0
