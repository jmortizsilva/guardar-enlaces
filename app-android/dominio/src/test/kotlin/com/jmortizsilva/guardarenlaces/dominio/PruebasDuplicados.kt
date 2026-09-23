package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebasNormalizarUrl {
    @Test
    fun `http y https son la misma pagina`() {
        assertTrue(Duplicados.esLaMisma("http://ejemplo.com/a", "https://ejemplo.com/a"))
    }

    @Test
    fun `el www, la barra final y el ancla no cuentan`() {
        assertTrue(
            Duplicados.esLaMisma("https://www.ejemplo.com/a/", "https://ejemplo.com/a#seccion")
        )
    }

    @Test
    fun `los parametros de seguimiento se descartan`() {
        assertTrue(
            Duplicados.esLaMisma(
                "https://ejemplo.com/a?utm_source=boletin&fbclid=xyz",
                "https://ejemplo.com/a",
            )
        )
    }

    @Test
    fun `pero los parametros que identifican el contenido si cuentan`() {
        assertFalse(
            Duplicados.esLaMisma("https://ejemplo.com/ver?id=1", "https://ejemplo.com/ver?id=2")
        )
    }

    @Test
    fun `el orden de los parametros da igual`() {
        assertTrue(
            Duplicados.esLaMisma("https://ejemplo.com/?b=2&a=1", "https://ejemplo.com/?a=1&b=2")
        )
    }

    @Test
    fun `dos parametros con la misma clave conservan su orden`() {
        assertFalse(
            Duplicados.esLaMisma("https://ejemplo.com/?a=1&a=2", "https://ejemplo.com/?a=2&a=1")
        )
    }

    @Test
    fun `la ruta distingue mayusculas, hay servidores donde no es lo mismo`() {
        assertFalse(Duplicados.esLaMisma("https://ejemplo.com/Uno", "https://ejemplo.com/uno"))
    }

    @Test
    fun `un mas en la consulta es un mas, no un espacio`() {
        // `URLDecoder` de Java es para formularios y lo convertiría en espacio: «c++» y «c  »
        // pasarían a ser la misma búsqueda.
        assertFalse(
            Duplicados.esLaMisma("https://ejemplo.com/?q=c++", "https://ejemplo.com/?q=c%20%20")
        )
    }

    @Test
    fun `lo que no es una URL se compara tal cual, sin inventar duplicados`() {
        assertEquals("esto no es una url", Duplicados.normalizar("  esto no es una url  "))
        assertFalse(Duplicados.esLaMisma("esto no es una url", "ni esto tampoco"))
    }

    @Test
    fun `dos cosas que no son URL tampoco se confunden entre si por estar vacias`() {
        // El fallo que esto caza es el de la app de Expo: el `URL` de React Native devolvía
        // anfitrión vacío para cualquier cosa, y todo lo que no fuera una URL se tomaba por
        // duplicado de lo anterior.
        assertNotEquals(Duplicados.normalizar("hola"), Duplicados.normalizar("adios"))
    }
}

class PruebasBuscarDuplicado {
    private val guardados =
        listOf(
            Elemento(id = "e1", url = "https://www.xataka.com/basics/alternativas-pocket"),
            Elemento(id = "e2", url = "https://ejemplo.com/otro"),
        )

    @Test
    fun `encuentra el que ya estaba, aunque venga escrito de otra forma`() {
        val encontrado =
            Duplicados.buscar(
                guardados,
                "http://xataka.com/basics/alternativas-pocket/?utm_source=twitter",
            )

        assertEquals("e1", encontrado?.id)
    }

    @Test
    fun `una URL nueva no es duplicado de nada`() {
        assertNull(Duplicados.buscar(guardados, "https://ejemplo.com/nuevo"))
    }

    @Test
    fun `lo borrado no cuenta, volver a guardarlo es un alta normal`() {
        val conBorrado =
            listOf(Elemento(id = "e3", url = "https://ejemplo.com/ido", borrado = true))

        assertNull(Duplicados.buscar(conBorrado, "https://ejemplo.com/ido"))
    }
}
