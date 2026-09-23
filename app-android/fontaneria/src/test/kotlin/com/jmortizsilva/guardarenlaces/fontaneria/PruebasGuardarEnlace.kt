package com.jmortizsilva.guardarenlaces.fontaneria

import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebasGuardarEnlace {
    private val almacen = almacenDePrueba()
    private val metadatos =
        MetadatosExtraidos(titulo = "Alternativas a Pocket", descripcion = "Una lista")

    @AfterTest fun cerrar() = almacen.cerrar()

    @Test
    fun `un enlace que no estaba se guarda y queda encolado`() {
        val resultado =
            GuardarEnlace.guardar("https://ejemplo.com", listOf("ocio"), metadatos, almacen) {
                1_000
            }

        assertIs<GuardarEnlace.Resultado.Nuevo>(resultado)
        assertEquals("Alternativas a Pocket", resultado.elemento.titulo)
        assertEquals(listOf("ocio"), resultado.elemento.etiquetas)
        assertNotNull(almacen.cargarPendientes()[resultado.elemento.id])
    }

    @Test
    fun `el enlace repetido actualiza el que habia, como en el ordenador`() {
        val primero =
            GuardarEnlace.guardar("https://ejemplo.com", listOf("ocio"), metadatos, almacen) {
                1_000
            }

        val segundo =
            GuardarEnlace.guardar("https://ejemplo.com", listOf("trabajo"), null, almacen) { 2_000 }

        assertIs<GuardarEnlace.Resultado.Actualizado>(segundo)
        assertEquals(primero.elemento.id, segundo.elemento.id)
        assertEquals(listOf("ocio", "trabajo"), segundo.elemento.etiquetas)
        // Sin metadatos nuevos se conserva el título: quedarse sin él por compartir el mismo
        // enlace sin cobertura sería perder información.
        assertEquals("Alternativas a Pocket", segundo.elemento.titulo)
        assertEquals(1, almacen.cargarTodos().size)
    }

    @Test
    fun `un enlace borrado y vuelto a guardar vuelve como nuevo`() {
        val primero =
            GuardarEnlace.guardar("https://ejemplo.com", emptyList(), metadatos, almacen) { 1_000 }
        almacen.marcarPendiente(primero.elemento.marcadoComoBorrado { 1_500 })

        val segundo =
            GuardarEnlace.guardar("https://ejemplo.com", emptyList(), metadatos, almacen) { 2_000 }

        // El borrado no cuenta como repetido: la fila sigue ahí de lápida, pero para quien guarda
        // el enlace ya no existe.
        assertIs<GuardarEnlace.Resultado.Nuevo>(segundo)
    }

    @Test
    fun `lo que se dice al guardar distingue los tres casos`() {
        val nuevo =
            GuardarEnlace.guardar("https://ejemplo.com", emptyList(), metadatos, almacen) { 1_000 }

        assertEquals("Guardado, Alternativas a Pocket", nuevo.anuncio(seComprobo = true))
        assertEquals(
            "Guardado sin título, no se pudo comprobar la página",
            nuevo.anuncio(seComprobo = false),
        )

        val repetido =
            GuardarEnlace.guardar("https://ejemplo.com", emptyList(), metadatos, almacen) { 2_000 }
        assertEquals("Actualizado, Alternativas a Pocket", repetido.anuncio(seComprobo = true))
    }
}

class PruebasCrearEtiqueta {
    private val almacen = almacenDePrueba()

    @AfterTest fun cerrar() = almacen.cerrar()

    @Test
    fun `la etiqueta nueva queda guardada y encolada`() {
        val etiqueta = CrearEtiqueta.crear("ocio", emptyList(), almacen) { 1_000 }

        assertEquals("ocio", etiqueta?.nombre)
        assertTrue(almacen.cargarEtiquetasDefinidas().values.any { it.nombre == "ocio" })
        assertEquals(1, almacen.cargarEtiquetasPendientes().size)
    }

    @Test
    fun `los espacios de alrededor no cuentan`() {
        assertEquals("trabajo", CrearEtiqueta.crear("  trabajo  ", emptyList(), almacen)?.nombre)
    }

    @Test
    fun `una que ya existe no se duplica, y no es un error`() {
        assertNull(CrearEtiqueta.crear("ocio", listOf("ocio"), almacen))
        assertTrue(almacen.cargarEtiquetasDefinidas().isEmpty())
    }

    @Test
    fun `un nombre en blanco no crea nada`() {
        assertNull(CrearEtiqueta.crear("   ", emptyList(), almacen))
        assertTrue(almacen.cargarEtiquetasDefinidas().isEmpty())
    }
}
