package com.jmortizsilva.guardarenlaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Adónde vuelve el cursor al volver. Sin Android: la pila es solo datos. */
class PruebasNavegacion {
    private val navegacion = Navegacion()

    @Test
    fun desde_la_lista_no_hay_adonde_volver() {
        assertFalse(navegacion.puedeVolver)
        navegacion.volver()
        assertEquals(Pantalla.Lista, navegacion.actual)
    }

    @Test
    fun al_salir_del_detalle_el_cursor_vuelve_a_su_fila() {
        navegacion.abrir(Pantalla.Detalle("e1"))
        navegacion.volver()

        assertEquals(Pantalla.Lista, navegacion.actual)
        assertEquals(Llegada.AFila("e1"), navegacion.llegada)
    }

    @Test
    fun al_salir_de_las_etiquetas_abiertas_desde_el_detalle_el_cursor_vuelve_a_su_boton() {
        navegacion.abrir(Pantalla.Detalle("e1"))
        navegacion.abrir(Pantalla.Etiquetas("e1"))
        navegacion.volver()

        assertEquals(Pantalla.Detalle("e1"), navegacion.actual)
        assertEquals(Llegada.ABotonEtiquetas(), navegacion.llegada)
    }

    @Test
    fun al_salir_de_las_etiquetas_abiertas_desde_la_lista_el_cursor_vuelve_a_la_fila() {
        navegacion.abrir(Pantalla.Etiquetas("e1"))
        navegacion.volver()

        assertEquals(Llegada.AFila("e1"), navegacion.llegada)
    }

    @Test
    fun una_llegada_explicita_manda_sobre_la_de_siempre() {
        navegacion.abrir(Pantalla.Detalle("e1"))
        navegacion.volver(Llegada.EliminarFila("e1"))

        assertEquals(Llegada.EliminarFila("e1"), navegacion.llegada)
    }

    @Test
    fun abrir_otra_pantalla_olvida_la_llegada_pendiente() {
        navegacion.abrir(Pantalla.Detalle("e1"))
        navegacion.volver()
        navegacion.abrir(Pantalla.Detalle("e2"))

        assertNull(navegacion.llegada)
    }

    @Test
    fun al_volver_a_anadir_desde_sus_etiquetas_el_cursor_va_al_boton() {
        navegacion.abrir(Pantalla.Anadir)
        navegacion.abrir(Pantalla.EtiquetasDelBorrador)
        navegacion.volver()

        assertEquals(Pantalla.Anadir, navegacion.actual)
        assertEquals(Llegada.ABotonEtiquetas(), navegacion.llegada)
    }
}
