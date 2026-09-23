package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals

class PruebasActualizarEnlace {
    private val guardado =
        Elemento(
            id = "e1",
            url = "https://a.com",
            titulo = "Título de antes",
            descripcion = "Descripción de antes",
            imagenUrl = "https://a.com/vieja.jpg",
            tipo = TipoElemento.Enlace,
            etiquetas = listOf("ocio"),
            creadoEn = 100,
            actualizadoEn = 100,
        )

    @Test
    fun `se refresca con lo que diga la comprobacion de ahora`() {
        val nuevos =
            MetadatosExtraidos(
                titulo = "Título de ahora",
                descripcion = "Descripción de ahora",
                imagenUrl = "https://a.com/nueva.jpg",
                tipo = TipoElemento.Articulo,
            )

        val resultado = guardado.actualizado(nuevos, ahora = relojFijo(500))

        assertEquals("e1", resultado.id)
        assertEquals("Título de ahora", resultado.titulo)
        assertEquals(TipoElemento.Articulo, resultado.tipo)
        assertEquals(500, resultado.actualizadoEn)
        assertEquals(100, resultado.creadoEn)
    }

    @Test
    fun `si la comprobacion no trajo nada, se conserva lo que ya habia`() {
        val resultado = guardado.actualizado(null, ahora = relojFijo(500))

        assertEquals("Título de antes", resultado.titulo)
        assertEquals("Descripción de antes", resultado.descripcion)
        assertEquals("https://a.com/vieja.jpg", resultado.imagenUrl)
    }

    @Test
    fun `las etiquetas se suman, no se sustituyen`() {
        val resultado =
            guardado.actualizado(
                null,
                etiquetasNuevas = listOf("pendiente"),
                ahora = relojFijo(500),
            )

        // Quitar en silencio una etiqueta puesta hace un mes sería peor que no guardar nada.
        assertEquals(listOf("ocio", "pendiente"), resultado.etiquetas)
    }

    @Test
    fun `una etiqueta que ya llevaba no se repite`() {
        val resultado = guardado.actualizado(null, etiquetasNuevas = listOf("ocio", "casa"))

        assertEquals(listOf("ocio", "casa"), resultado.etiquetas)
    }

    @Test
    fun `un titulo que llega vacio no borra el que habia`() {
        val resultado = guardado.actualizado(MetadatosExtraidos())

        assertEquals("Título de antes", resultado.titulo)
    }
}
