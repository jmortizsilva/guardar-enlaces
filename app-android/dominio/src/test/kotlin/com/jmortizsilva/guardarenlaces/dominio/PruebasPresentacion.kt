package com.jmortizsilva.guardarenlaces.dominio

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mediodía UTC del 15 de marzo de 2024. A mediodía, y con la zona fijada en la prueba, la fecha no
 * se va al día de antes ni al de después según dónde se ejecute esto.
 */
private const val MEDIODIA_UTC: MarcaDeTiempo = 1_710_504_000_000

private fun elemento(
    url: String,
    titulo: String? = null,
    etiquetas: List<String> = emptyList(),
    creadoEn: MarcaDeTiempo = MEDIODIA_UTC,
) =
    Elemento(
        id = "e1",
        url = url,
        titulo = titulo,
        etiquetas = etiquetas,
        creadoEn = creadoEn,
        // Muy posterior a la de guardado: la fila no debe enseñar esta.
        actualizadoEn = creadoEn + 30 * 86_400_000L,
    )

private fun subtitulo(elemento: Elemento) = Presentacion.subtitulo(elemento, zona = ZoneOffset.UTC)

class PruebasTituloFila {
    @Test
    fun `usa el titulo cuando lo hay`() {
        assertEquals("A", Presentacion.titulo(elemento("https://a.com", titulo = "A")))
    }

    @Test
    fun `sin titulo usa la URL, que es mejor que una fila muda`() {
        assertEquals("https://a.com", Presentacion.titulo(elemento("https://a.com")))
    }

    @Test
    fun `un titulo vacio cuenta como no tener titulo`() {
        assertEquals("https://a.com", Presentacion.titulo(elemento("https://a.com", titulo = "")))
    }
}

class PruebasSubtituloFila {
    @Test
    fun `lleva dominio, etiquetas y fecha, en ese orden`() {
        val uno =
            elemento(
                "https://www.ejemplo.com/articulo",
                titulo = "Un artículo interesante",
                etiquetas = listOf("ocio", "pendiente"),
            )

        assertEquals("www.ejemplo.com — ocio, pendiente — 15 de marzo de 2024", subtitulo(uno))
    }

    @Test
    fun `la fecha lleva el mes en letra, que es como se oye bien`() {
        assertTrue("de marzo de" in subtitulo(elemento("https://a.com")))
    }

    @Test
    fun `sin titulo el subtitulo sigue diciendo el dominio`() {
        assertTrue(subtitulo(elemento("https://a.com")).startsWith("a.com"))
    }

    @Test
    fun `sin etiquetas no queda un separador vacio en medio`() {
        assertFalse(" —  — " in subtitulo(elemento("https://a.com", titulo = "A")))
    }

    @Test
    fun `sin fecha lo dice, en vez de soltar 1970`() {
        assertTrue("sin fecha" in subtitulo(elemento("https://a.com", creadoEn = 0)))
    }

    @Test
    fun `la fecha es la de cuando se guardo, no la del ultimo cambio`() {
        // Cambiarle una etiqueta a un enlace de hace un mes movía la fecha que se lee en la fila, y
        // entonces ya no había forma de saber cuándo se había guardado de verdad.
        val retocadoHoy =
            elemento("https://a.com", titulo = "A")
                .conEtiquetas(listOf("ocio"), ahora = { MEDIODIA_UTC + 99_999_999 })

        assertTrue("15 de marzo de 2024" in subtitulo(retocadoHoy))
    }

    @Test
    fun `lo que no es una URL no aporta dominio, pero tampoco rompe la fila`() {
        assertEquals(
            "15 de marzo de 2024",
            subtitulo(elemento("esto no es una url", titulo = "Raro")),
        )
    }

    @Test
    fun `la fecha va en español aunque el telefono este en otro idioma`() {
        // La zona y el idioma del teléfono no se usan para la fecha: se fija el español de la app.
        assertEquals("es", Presentacion.localeDeLaApp.language)
        assertEquals("ES", Presentacion.localeDeLaApp.country)
    }
}

class PruebasTextosDetalle {
    @Test
    fun `la fecha de guardado se lee, no se descifra`() {
        val fecha =
            Presentacion.fechaLegible(MEDIODIA_UTC, Presentacion.localeDeLaApp, ZoneOffset.UTC)

        assertEquals("Guardado el 15 de marzo de 2024", Textos.guardadoEl(fecha))
    }

    @Test
    fun `un enlace sin fecha lo dice en vez de soltar 1970`() {
        assertEquals(
            "sin fecha",
            Presentacion.fechaLegible(0, Presentacion.localeDeLaApp, ZoneOffset.UTC),
        )
    }
}
