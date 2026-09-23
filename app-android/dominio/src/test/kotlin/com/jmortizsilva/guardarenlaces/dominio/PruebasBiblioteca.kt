package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun elemento(
    id: String,
    url: String = "https://a.com",
    titulo: String? = null,
    etiquetas: List<String> = emptyList(),
) = Elemento(id = id, url = url, titulo = titulo, etiquetas = etiquetas, actualizadoEn = 100)

class PruebasBuscar {
    private val a = elemento("a", url = "https://python.org", titulo = "Documentación Python")
    private val b =
        elemento("b", url = "https://otra.com", titulo = "Otra cosa", etiquetas = listOf("python"))
    private val c = elemento("c", url = "https://nada.com", titulo = "Nada que ver")

    @Test
    fun `busca en titulo, URL y etiquetas, sin distinguir mayusculas`() {
        assertEquals(listOf("a", "b"), Biblioteca.buscar(listOf(a, b, c), "PYTHON").map { it.id })
    }

    @Test
    fun `sin texto no filtra`() {
        assertEquals(3, Biblioteca.buscar(listOf(a, b, c), "").size)
        assertEquals(3, Biblioteca.buscar(listOf(a, b, c), "   ").size)
    }

    @Test
    fun `lo que no esta no aparece`() {
        assertTrue(Biblioteca.buscar(listOf(a, b, c), "no-existe").isEmpty())
    }
}

class PruebasEtiquetasEnUso {
    @Test
    fun `las distintas, ordenadas y sin repetir`() {
        val a = elemento("a", etiquetas = listOf("ocio", "pendiente"))
        val b = elemento("b", etiquetas = listOf("trabajo", "ocio"))
        val c = elemento("c")

        assertEquals(
            listOf("ocio", "pendiente", "trabajo"),
            Biblioteca.etiquetasEnUso(listOf(a, b, c)),
        )
    }

    @Test
    fun `se ordenan como en un diccionario, con las tildes en su sitio`() {
        // Comparando códigos de letra, «ábaco» iría detrás de «zumo» y «Casa» delante de «barco».
        val a = elemento("a", etiquetas = listOf("zumo", "ábaco", "Casa", "barco"))

        assertEquals(listOf("ábaco", "barco", "Casa", "zumo"), Biblioteca.etiquetasEnUso(listOf(a)))
    }

    @Test
    fun `las disponibles suman las reservadas, sin repetir y en orden`() {
        val a = elemento("a", etiquetas = listOf("ocio", "trabajo"))

        assertEquals(
            listOf("casa", "ocio", "trabajo"),
            Biblioteca.etiquetasDisponibles(listOf(a), reservadas = listOf("ocio", "casa")),
        )
    }

    @Test
    fun `sin elementos, o sin etiquetas, no hay ninguna`() {
        assertTrue(Biblioteca.etiquetasEnUso(emptyList()).isEmpty())
        assertTrue(Biblioteca.etiquetasEnUso(listOf(elemento("a"))).isEmpty())
    }

    @Test
    fun `cuenta cuantos enlaces lleva cada una`() {
        val a = elemento("a", etiquetas = listOf("ocio", "trabajo"))
        val b = elemento("b", etiquetas = listOf("ocio"))
        val c = elemento("c")

        assertEquals(
            mapOf("ocio" to 2, "trabajo" to 1),
            Biblioteca.recuentoPorEtiqueta(listOf(a, b, c)),
        )
    }
}

class PruebasFiltrarPorEtiqueta {
    private val a = elemento("a", etiquetas = listOf("ocio"))
    private val b = elemento("b", etiquetas = listOf("trabajo"))

    @Test
    fun `deja los que la llevan`() {
        assertEquals(listOf("a"), Biblioteca.filtrarPorEtiqueta(listOf(a, b), "ocio").map { it.id })
    }

    @Test
    fun `sin etiqueta elegida no filtra`() {
        assertEquals(2, Biblioteca.filtrarPorEtiqueta(listOf(a, b), null).size)
        assertEquals(2, Biblioteca.filtrarPorEtiqueta(listOf(a, b), "").size)
    }
}

class PruebasRenombrarEtiqueta {
    @Test
    fun `devuelve solo los enlaces que cambian, con la fecha movida`() {
        val a = elemento("a", etiquetas = listOf("ocio"))
        val b = elemento("b", etiquetas = listOf("trabajo"))

        val cambiados =
            Biblioteca.renombrarEtiqueta(listOf(a, b), "ocio", "tiempo libre", relojFijo(200))

        assertEquals(listOf("a"), cambiados.map { it.id })
        assertEquals(listOf("tiempo libre"), cambiados[0].etiquetas)
        assertEquals(200, cambiados[0].actualizadoEn)
    }

    @Test
    fun `renombrar a una que el enlace ya tenia las funde, no la repite`() {
        val a = elemento("a", etiquetas = listOf("ocio", "casa"))

        val cambiados = Biblioteca.renombrarEtiqueta(listOf(a), "ocio", "casa")

        assertEquals(listOf("casa"), cambiados[0].etiquetas)
    }

    @Test
    fun `quitar una etiqueta deja el resto en paz`() {
        val a = elemento("a", etiquetas = listOf("ocio", "casa"))
        val b = elemento("b", etiquetas = listOf("trabajo"))

        val cambiados = Biblioteca.quitarEtiqueta(listOf(a, b), "ocio", relojFijo(200))

        assertEquals(listOf("a"), cambiados.map { it.id })
        assertEquals(listOf("casa"), cambiados[0].etiquetas)
        assertEquals(200, cambiados[0].actualizadoEn)
    }
}
