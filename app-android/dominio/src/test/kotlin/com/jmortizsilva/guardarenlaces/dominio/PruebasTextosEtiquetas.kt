package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PruebasTextosEtiquetas {
    @Test
    fun `cada fila dice cuantos enlaces lleva la etiqueta, con su plural`() {
        assertEquals("ocio, ningún enlace", Textos.etiquetaConRecuento("ocio", 0))
        assertEquals("ocio, 1 enlace", Textos.etiquetaConRecuento("ocio", 1))
        assertEquals("ocio, 5 enlaces", Textos.etiquetaConRecuento("ocio", 5))
    }

    @Test
    fun `antes de eliminar se dice a cuantos enlaces afecta y que no se deshace`() {
        assertEquals(
            "¿Eliminar la etiqueta «ocio»? Se quitará de 1 enlace y no se puede deshacer.",
            Textos.preguntaEliminarEtiqueta("ocio", 1),
        )
        assertTrue("de 3 enlaces" in Textos.preguntaEliminarEtiqueta("ocio", 3))
    }

    @Test
    fun `al renombrar se dice en cuantos enlaces ha cambiado`() {
        assertEquals(
            "Etiqueta «ocio» renombrada a «tiempo libre» en 2 enlaces",
            Textos.etiquetaRenombrada("ocio", "tiempo libre", 2),
        )
        assertTrue(Textos.etiquetaRenombrada("a", "b", 1).endsWith("en 1 enlace"))
    }

    @Test
    fun `al eliminar y al crear tambien se dice el resultado`() {
        assertEquals("Etiqueta «ocio» eliminada de 4 enlaces", Textos.etiquetaEliminada("ocio", 4))
        assertEquals("Etiqueta «casa» añadida", Textos.etiquetaAnadida("casa"))
    }
}
