package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class PruebasComprobarDireccion {
    private val completada = DireccionEscrita("https://viejo.es", "http://viejo.es")
    private val escritaEntera = DireccionEscrita("https://viejo.es", null)
    private val carga = Comprobacion.Carga(MetadatosExtraidos(titulo = "Viejo"))

    /** Responde según la dirección, y apunta cuáles se han preguntado. */
    private class Respuestas(val porDireccion: Map<String, Comprobacion>) {
        val preguntadas = mutableListOf<String>()

        suspend fun comprobar(url: String): Comprobacion {
            preguntadas += url
            return porDireccion.getValue(url)
        }
    }

    @Test
    fun `si carga con https no se prueba nada mas`() = runBlocking {
        val respuestas = Respuestas(mapOf("https://viejo.es" to carga))

        val resultado = ComprobarDireccion.comprobar(completada, respuestas::comprobar)

        assertEquals(DireccionComprobada("https://viejo.es", carga), resultado)
        assertEquals(listOf("https://viejo.es"), respuestas.preguntadas)
    }

    @Test
    fun `si no carga con https y lo puso la app, se queda con http si carga`() = runBlocking {
        val respuestas =
            Respuestas(
                mapOf("https://viejo.es" to Comprobacion.NoCarga, "http://viejo.es" to carga)
            )

        val resultado = ComprobarDireccion.comprobar(completada, respuestas::comprobar)

        assertEquals(DireccionComprobada("http://viejo.es", carga), resultado)
    }

    @Test
    fun `si no carga ninguna, se queda con la de https`() = runBlocking {
        val respuestas =
            Respuestas(
                mapOf(
                    "https://viejo.es" to Comprobacion.NoCarga,
                    "http://viejo.es" to Comprobacion.NoCarga,
                )
            )

        val resultado = ComprobarDireccion.comprobar(completada, respuestas::comprobar)

        assertEquals(DireccionComprobada("https://viejo.es", Comprobacion.NoCarga), resultado)
    }

    @Test
    fun `si el esquema lo escribio quien usa la app, no se prueba otro`() = runBlocking {
        val respuestas = Respuestas(mapOf("https://viejo.es" to Comprobacion.NoCarga))

        val resultado = ComprobarDireccion.comprobar(escritaEntera, respuestas::comprobar)

        assertEquals(Comprobacion.NoCarga, resultado.comprobacion)
        assertEquals(listOf("https://viejo.es"), respuestas.preguntadas)
    }

    @Test
    fun `sin poder comprobar no se prueba la alternativa`() = runBlocking {
        val respuestas = Respuestas(mapOf("https://viejo.es" to Comprobacion.SinComprobar))

        val resultado = ComprobarDireccion.comprobar(completada, respuestas::comprobar)

        assertEquals(Comprobacion.SinComprobar, resultado.comprobacion)
        assertEquals(listOf("https://viejo.es"), respuestas.preguntadas)
    }
}
