package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PruebasReloj {
    @Test
    fun `el identificador es un uuid v4 en minusculas`() {
        val id = generarIdUnico()
        assertEquals(id.lowercase(), id)
        assertEquals(36, id.length)
        assertEquals('4', id[14])
    }

    @Test
    fun `dos identificadores no se repiten`() {
        assertNotEquals(generarIdUnico(), generarIdUnico())
    }
}
