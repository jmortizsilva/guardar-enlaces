"""Elegir etiquetas de las que ya hay, y crear una nueva.

Mismas reglas que PantallaEtiquetas.swift en el iPhone: la etiqueta que
acabas de escribir se queda marcada, y escribir una que ya existe no crea una
fila repetida, solo la marca.
"""

from __future__ import annotations

import pytest
import wx

from guardar_enlaces.ui.selector_etiquetas import SelectorEtiquetas


@pytest.fixture
def marco(app):
    marco = wx.Frame(None)
    yield marco
    marco.Destroy()


def _selector(marco, disponibles=(), elegidas=()) -> SelectorEtiquetas:
    return SelectorEtiquetas(marco, disponibles, elegidas)


def _filas(selector: SelectorEtiquetas) -> list[str]:
    return [selector.lista.GetItemText(f) for f in range(selector.lista.GetItemCount())]


def _crear(selector: SelectorEtiquetas, nombre: str) -> str | None:
    selector.campo_nueva.SetValue(nombre)
    return selector.anadir_nueva()


class TestLoQueSeVe:
    def test_salen_las_disponibles_ordenadas(self, marco):
        s = _selector(marco, ["Recetas", "podcasts", "Accesibilidad"])
        assert _filas(s) == ["Accesibilidad", "podcasts", "Recetas"]

    def test_el_orden_no_manda_las_tildes_al_final(self, marco):
        """Con el orden de Python a secas, "Ávila" cae detras de "Zamora"."""
        s = _selector(marco, ["Zamora", "Ávila"])
        assert _filas(s) == ["Ávila", "Zamora"]

    def test_las_del_enlace_salen_marcadas(self, marco):
        s = _selector(marco, ["Recetas", "Podcasts"], ["Podcasts"])
        assert s.etiquetas_elegidas() == ("Podcasts",)

    def test_una_etiqueta_del_enlace_que_no_tiene_nadie_mas_tambien_sale(self, marco):
        """Si no, editar un enlace le borraria en silencio una etiqueta suya
        solo por ser la unica que la lleva."""
        s = _selector(marco, ["Recetas"], ["Rarisima"])
        assert "Rarisima" in _filas(s)
        assert s.etiquetas_elegidas() == ("Rarisima",)

    def test_sin_etiquetas_no_revienta(self, marco):
        s = _selector(marco)
        assert _filas(s) == []
        assert s.etiquetas_elegidas() == ()


class TestMarcarYDesmarcar:
    def test_marcar_una_casilla_la_elige(self, marco):
        s = _selector(marco, ["Recetas", "Podcasts"])
        s.lista.CheckItem(_filas(s).index("Recetas"), True)
        assert s.etiquetas_elegidas() == ("Recetas",)

    def test_desmarcar_la_quita(self, marco):
        s = _selector(marco, ["Recetas", "Podcasts"], ["Recetas", "Podcasts"])
        s.lista.CheckItem(_filas(s).index("Recetas"), False)
        assert s.etiquetas_elegidas() == ("Podcasts",)

    def test_lo_elegido_sale_ordenado(self, marco):
        s = _selector(marco, ["Zamora", "Ávila", "Burgos"], ["Zamora", "Ávila"])
        assert s.etiquetas_elegidas() == ("Ávila", "Zamora")


class TestCrearUnaNueva:
    def test_la_nueva_se_anade_y_queda_marcada(self, marco):
        """Como en el iPhone: quien la escribe la quiere para este enlace."""
        s = _selector(marco, ["Recetas"])
        assert _crear(s, "Podcasts") == "Podcasts"
        assert "Podcasts" in _filas(s)
        assert s.etiquetas_elegidas() == ("Podcasts",)

    def test_no_se_pierde_lo_que_ya_estaba_marcado(self, marco):
        """La lista se repinta entera al crear: si lo marcado no se lee antes,
        se borra sin que nadie se entere."""
        s = _selector(marco, ["Recetas"], ["Recetas"])
        _crear(s, "Podcasts")
        assert s.etiquetas_elegidas() == ("Podcasts", "Recetas")

    def test_escribir_una_que_ya_existe_no_duplica_la_fila(self, marco):
        s = _selector(marco, ["Recetas"])
        _crear(s, "Recetas")
        assert _filas(s) == ["Recetas"]
        assert s.etiquetas_elegidas() == ("Recetas",)

    def test_se_le_quitan_los_espacios_de_los_lados(self, marco):
        s = _selector(marco)
        assert _crear(s, "  Podcasts  ") == "Podcasts"
        assert _filas(s) == ["Podcasts"]

    def test_en_blanco_no_crea_nada(self, marco):
        s = _selector(marco, ["Recetas"])
        assert _crear(s, "   ") is None
        assert _filas(s) == ["Recetas"]
        assert s.etiquetas_elegidas() == ()

    def test_el_campo_se_vacia_para_poder_escribir_otra(self, marco):
        s = _selector(marco)
        _crear(s, "Podcasts")
        assert s.campo_nueva.GetValue() == ""

    def test_el_foco_se_va_a_la_etiqueta_recien_creada(self, marco):
        """Es la unica confirmacion que recibe quien no ve la lista: el lector
        lee su nombre y que esta marcada. Callarse no se distingue de que no
        haya pasado nada."""
        s = _selector(marco, ["Recetas", "Zamora"])
        _crear(s, "Podcasts")
        fila = _filas(s).index("Podcasts")
        assert s.lista.GetFocusedItem() == fila
        assert s.lista.IsItemChecked(fila)

    def test_la_nueva_aparece_en_su_sitio_por_orden(self, marco):
        s = _selector(marco, ["Accesibilidad", "Zamora"])
        _crear(s, "Podcasts")
        assert _filas(s) == ["Accesibilidad", "Podcasts", "Zamora"]
