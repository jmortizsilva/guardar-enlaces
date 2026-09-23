package com.jmortizsilva.guardarenlaces.fontaneria

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.EtiquetaDefinida
import com.jmortizsilva.guardarenlaces.dominio.MarcaDeTiempo
import com.jmortizsilva.guardarenlaces.dominio.TipoElemento
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Base de datos en memoria: cada prueba estrena la suya y no deja ficheros. */
fun almacenDePrueba() = AlmacenLocal(BundledSQLiteDriver(), ":memory:")

private fun elemento(
    id: String,
    url: String = "https://a.com",
    titulo: String? = "Un título",
    actualizadoEn: MarcaDeTiempo = 100,
    borrado: Boolean = false,
) =
    Elemento(
        id = id,
        url = url,
        titulo = titulo,
        creadoEn = 50,
        actualizadoEn = actualizadoEn,
        borrado = borrado,
    )

class PruebasGuardarElementos {
    private val almacen = almacenDePrueba()

    @AfterTest fun cerrar() = almacen.cerrar()

    @Test
    fun `un enlace completo vuelve tal cual de la base de datos`() {
        val original =
            Elemento(
                id = "e1",
                url = "https://a.com/artículo",
                titulo = "Café & teoría",
                descripcion = "Una descripción",
                imagenUrl = "https://a.com/i.jpg",
                tipo = TipoElemento.Articulo,
                etiquetas = listOf("ocio", "pendiente"),
                creadoEn = 50,
                actualizadoEn = 100,
            )

        almacen.guardar(mapOf(original.id to original))

        assertEquals(original, almacen.cargarTodos()["e1"])
    }

    @Test
    fun `los campos vacios siguen vacios, no se vuelven cadenas`() {
        almacen.guardar(mapOf("e1" to Elemento(id = "e1", url = "https://a.com")))
        val leido = almacen.cargarTodos()["e1"]

        assertNull(leido?.titulo)
        assertNull(leido?.descripcion)
        assertNull(leido?.imagenUrl)
        assertEquals(emptyList(), leido?.etiquetas)
    }

    @Test
    fun `guardar dos veces el mismo identificador actualiza, no duplica`() {
        val primero = elemento("e1", titulo = "Viejo")
        almacen.guardar(mapOf(primero.id to primero))

        val segundo = primero.conEtiquetas(listOf("nueva"), ahora = { 200 })
        almacen.guardar(mapOf(segundo.id to segundo))

        val todos = almacen.cargarTodos()
        assertEquals(1, todos.size)
        assertEquals(listOf("nueva"), todos["e1"]?.etiquetas)
        assertEquals(200, todos["e1"]?.actualizadoEn)
    }
}

class PruebasColaPendientes {
    private val almacen = almacenDePrueba()

    @AfterTest fun cerrar() = almacen.cerrar()

    @Test
    fun `un cambio local se ve al momento y queda encolado`() {
        val uno = elemento("e1")

        almacen.marcarPendiente(uno)

        assertEquals(1, almacen.cargarTodos().size)
        assertEquals(uno, almacen.cargarPendientes()["e1"])
    }

    @Test
    fun `lo guardado por una sincronizacion no se encola`() {
        almacen.guardar(mapOf("e1" to elemento("e1")))

        assertTrue(almacen.cargarPendientes().isEmpty())
    }

    @Test
    fun `limpiar saca de la cola pero no borra el enlace`() {
        almacen.marcarPendiente(elemento("e1"))
        almacen.marcarPendiente(elemento("e2"))

        almacen.limpiarPendientes(listOf("e1"))

        assertEquals(listOf("e2"), almacen.cargarPendientes().keys.sorted())
        assertEquals(2, almacen.cargarTodos().size)
    }

    @Test
    fun `varios cambios de golpe quedan todos encolados`() {
        almacen.marcarPendientes(listOf(elemento("e1"), elemento("e2")))

        assertEquals(setOf("e1", "e2"), almacen.cargarPendientes().keys)
    }
}

class PruebasTransacciones {
    private val almacen = almacenDePrueba()

    @AfterTest fun cerrar() = almacen.cerrar()

    @Test
    fun `si algo falla a mitad, no queda nada a medias`() {
        // Una bajada que se corta a la mitad no puede dejar la mitad de los enlaces guardados con
        // el cursor sin mover, ni el cursor movido sin los enlaces.
        assertFailsWith<IllegalStateException> {
            almacen.enTransaccion {
                almacen.guardar(mapOf("e1" to elemento("e1")))
                almacen.fijarCursor(900)
                error("se cortó")
            }
        }

        assertTrue(almacen.cargarTodos().isEmpty())
        assertEquals(0, almacen.cursor())
    }

    @Test
    fun `una transaccion dentro de otra se suma a la de fuera`() {
        almacen.enTransaccion {
            almacen.marcarPendiente(elemento("e1"))
            almacen.enTransaccion { almacen.fijarCursor(5) }
        }

        assertEquals(1, almacen.cargarPendientes().size)
        assertEquals(5, almacen.cursor())
    }
}

class PruebasDuenoYCursor {
    private val almacen = almacenDePrueba()

    @AfterTest fun cerrar() = almacen.cerrar()

    @Test
    fun `el dueno y el cursor se recuerdan, y de fabrica no hay ninguno`() {
        assertNull(almacen.duenoActual())
        assertEquals(0, almacen.cursor())

        almacen.fijarDueno("https://api.ejemplo.com|persona@ejemplo.com")
        almacen.fijarCursor(1_735_000_000_000)

        assertEquals("https://api.ejemplo.com|persona@ejemplo.com", almacen.duenoActual())
        assertEquals(1_735_000_000_000, almacen.cursor())
    }

    @Test
    fun `contar enlaces no cuenta las lapidas`() {
        almacen.guardar(mapOf("e1" to elemento("e1"), "e2" to elemento("e2", borrado = true)))

        assertEquals(1, almacen.contarElementos())
    }

    @Test
    fun `vaciar se lo lleva todo, incluidos el cursor y el dueno`() {
        almacen.marcarPendiente(elemento("e1"))
        almacen.marcarEtiquetaPendiente(EtiquetaDefinida(id = "t1", nombre = "ocio"))
        almacen.fijarDueno("alguien")
        almacen.fijarCursor(500)

        almacen.vaciar()

        assertTrue(almacen.cargarTodos().isEmpty())
        assertTrue(almacen.cargarPendientes().isEmpty())
        assertTrue(almacen.cargarEtiquetasDefinidas().isEmpty())
        assertTrue(almacen.cargarEtiquetasPendientes().isEmpty())
        assertNull(almacen.duenoActual())
        assertEquals(0, almacen.cursor())
    }
}

class PruebasAdoptar {
    private val almacen = almacenDePrueba()

    @AfterTest fun cerrar() = almacen.cerrar()

    @Test
    fun `todo cambia de identificador y queda listo para subir`() {
        almacen.guardar(mapOf("e1" to elemento("e1"), "e2" to elemento("e2")))
        almacen.guardarEtiquetasDefinidas(
            mapOf("t1" to EtiquetaDefinida(id = "t1", nombre = "ocio"))
        )

        var contador = 0
        almacen.adoptarConIdsNuevos(generarId = { "nuevo-${++contador}" })

        val todos = almacen.cargarTodos()
        assertEquals(2, todos.size)
        assertTrue(todos.keys.all { it.startsWith("nuevo-") })
        // Si no entran en la cola, la cuenta nueva se queda sin ellos.
        assertEquals(2, almacen.cargarPendientes().size)
        assertEquals(1, almacen.cargarEtiquetasPendientes().size)
    }

    @Test
    fun `las lapidas se tiran, son el borrado de otra cuenta`() {
        almacen.guardar(mapOf("e1" to elemento("e1"), "e2" to elemento("e2", borrado = true)))

        almacen.adoptarConIdsNuevos()

        assertEquals(1, almacen.cargarTodos().size)
    }
}

class PruebasEtiquetasEnAlmacen {
    private val almacen = almacenDePrueba()

    @AfterTest fun cerrar() = almacen.cerrar()

    @Test
    fun `una etiqueta vuelve tal cual, y su cola funciona igual que la de enlaces`() {
        val etiqueta =
            EtiquetaDefinida(id = "t1", nombre = "ocio", creadoEn = 50, actualizadoEn = 100)

        almacen.marcarEtiquetaPendiente(etiqueta)

        assertEquals(etiqueta, almacen.cargarEtiquetasDefinidas()["t1"])
        assertEquals(etiqueta, almacen.cargarEtiquetasPendientes()["t1"])

        almacen.limpiarEtiquetasPendientes(listOf("t1"))

        assertTrue(almacen.cargarEtiquetasPendientes().isEmpty())
        assertEquals(1, almacen.cargarEtiquetasDefinidas().size)
    }
}
