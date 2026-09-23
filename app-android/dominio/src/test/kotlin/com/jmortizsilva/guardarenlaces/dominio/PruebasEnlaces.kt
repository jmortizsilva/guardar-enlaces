package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebasEnlaces {
    @Test
    fun `se aceptan las dos formas de direccion`() {
        assertTrue(Enlaces.esDireccion("https://ejemplo.com"))
        assertTrue(Enlaces.esDireccion("http://ejemplo.com"))
        assertTrue(Enlaces.esDireccion("  https://ejemplo.com  "))
    }

    @Test
    fun `lo que no es una direccion se rechaza`() {
        assertFalse(Enlaces.esDireccion("ejemplo.com"))
        assertFalse(Enlaces.esDireccion("nota para el lunes"))
        assertFalse(Enlaces.esDireccion(""))
        assertFalse(Enlaces.esDireccion("   "))
    }

    @Test
    fun `el esquema a secas no es una direccion`() {
        assertFalse(Enlaces.esDireccion("https://"))
        assertFalse(Enlaces.esDireccion("http://"))
    }

    @Test
    fun `la direccion se saca de la frase en la que venga`() {
        assertEquals(
            "https://ejemplo.com/articulo",
            Enlaces.direccionDentroDe("Mira esto: https://ejemplo.com/articulo"),
        )
        assertEquals(
            "https://ejemplo.com",
            Enlaces.direccionDentroDe("https://ejemplo.com vía @alguien"),
        )
    }

    @Test
    fun `un espacio duro delante tambien separa`() {
        // `\s` en Java no lo incluye, y hay aplicaciones que lo ponen justo antes de la dirección.
        assertEquals(
            "https://ejemplo.com",
            Enlaces.direccionDentroDe("Mira esto: https://ejemplo.com"),
        )
    }

    @Test
    fun `una direccion sola vuelve tal cual, sin los espacios de alrededor`() {
        assertEquals("https://ejemplo.com", Enlaces.direccionDentroDe(" https://ejemplo.com\n"))
    }

    @Test
    fun `un texto sin ninguna direccion no devuelve nada`() {
        assertNull(Enlaces.direccionDentroDe("la lista de la compra"))
        assertNull(Enlaces.direccionDentroDe(""))
    }

    @Test
    fun `con dos direcciones se queda con la primera`() {
        assertEquals(
            "https://uno.com",
            Enlaces.direccionDentroDe("https://uno.com y también https://dos.com"),
        )
    }
}
