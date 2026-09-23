package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PruebasReloj {
    @Test
    fun `el reloj del sistema cuenta en milisegundos, no en segundos`() {
        // 1.700.000.000.000 ms es noviembre de 2023. El mismo instante contado en segundos da un
        // número mil veces menor, que es justo el fallo que esto tiene que cazar.
        assertTrue(relojDelSistema() > 1_700_000_000_000)
    }

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
