package com.jmortizsilva.guardarenlaces.fontaneria

import com.jmortizsilva.guardarenlaces.dominio.Elemento
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PruebasSincronizador {
    private val servidor = ServidorFalso()
    private val almacen = almacenDePrueba()
    private val sincronizador: Sincronizador

    init {
        val cliente = servidor.cliente()
        // Sesión ya iniciada: lo que se prueba aquí es el ciclo, no el inicio de sesión.
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"tokenAcceso": "acceso", "expiraEn": 1, "tokenRefresco": "refresco2"}"""
            )
        val sesion = Sesion(cliente, CredencialesEnMemoria("refresco"))
        runBlocking { sesion.restaurar() }
        sincronizador = Sincronizador(almacen, cliente, sesion)
    }

    @AfterTest
    fun cerrar() {
        servidor.close()
        almacen.cerrar()
    }

    /** Contesta a la subida y a la bajada lo que le digan. */
    private fun responder(subida: String, bajada: String) {
        servidor.manejador = { pedida ->
            ServidorFalso.Respuesta.json(if (pedida.metodo == "GET") bajada else subida)
        }
    }

    private fun peticionesASincronizar() = servidor.rutasPedidas.count { it == "/sincronizar" }

    @Test
    fun `sin nada pendiente no se sube nada, pero si se baja`() = runBlocking {
        responder(subida = "{}", bajada = """{"elementos": [], "servidorEn": 700}""")

        sincronizador.sincronizar()

        assertEquals(1, peticionesASincronizar())
        assertEquals(700, almacen.cursor())
    }

    @Test
    fun `lo pendiente se sube y sale de la cola`() = runBlocking {
        almacen.marcarPendiente(Elemento(id = "e1", url = "https://a.com", actualizadoEn = 100))
        responder(
            subida =
                """{"elementos": [{"id": "e1", "url": "https://a.com", "titulo": "Lo puso el servidor",
                    "actualizadoEn": 150}], "rechazados": []}""",
            bajada = """{"elementos": [], "servidorEn": 700}""",
        )

        sincronizador.sincronizar()

        assertTrue(almacen.cargarPendientes().isEmpty())
        // Manda la versión del servidor, aunque difiera de lo que se envió.
        assertEquals("Lo puso el servidor", almacen.cargarTodos()["e1"]?.titulo)
    }

    @Test
    fun `lo rechazado tambien sale de la cola, o se reenvia para siempre`() = runBlocking {
        almacen.marcarPendiente(Elemento(id = "e1", url = "https://a.com", actualizadoEn = 100))
        responder(
            subida =
                """{"elementos": [], "rechazados": [{"id": "e1", "motivo": "no_aplicable"}]}""",
            bajada = """{"elementos": [], "servidorEn": 700}""",
        )

        val rechazados = sincronizador.sincronizar()

        assertEquals(1, rechazados)
        assertTrue(almacen.cargarPendientes().isEmpty())
    }

    @Test
    fun `lo que baja se guarda y el cursor queda en la hora del servidor`() = runBlocking {
        responder(
            subida = "{}",
            bajada =
                """{"elementos": [{"id": "e9", "url": "https://b.com", "actualizadoEn": 300}],
                   "etiquetasDefinidas": [{"id": "t1", "nombre": "ocio", "actualizadoEn": 250}],
                   "servidorEn": 900, "masDisponible": false}""",
        )

        sincronizador.sincronizar()

        assertEquals("https://b.com", almacen.cargarTodos()["e9"]?.url)
        assertEquals("ocio", almacen.cargarEtiquetasDefinidas()["t1"]?.nombre)
        assertEquals(900, almacen.cursor())
    }

    @Test
    fun `con mas paginas pendientes repite la bajada desde el ultimo cambio`() = runBlocking {
        val vueltas = AtomicInteger()
        servidor.manejador = { pedida ->
            if (pedida.metodo != "GET") ServidorFalso.Respuesta.json("{}")
            else if (vueltas.incrementAndGet() == 1)
                ServidorFalso.Respuesta.json(
                    """{"elementos": [{"id": "e1", "url": "https://a.com", "actualizadoEn": 500}],
                       "servidorEn": 0, "masDisponible": true}"""
                )
            else
                ServidorFalso.Respuesta.json(
                    """{"elementos": [{"id": "e2", "url": "https://b.com", "actualizadoEn": 600}],
                       "servidorEn": 900, "masDisponible": false}"""
                )
        }

        sincronizador.sincronizar()

        assertEquals(2, almacen.cargarTodos().size)
        assertEquals(900, almacen.cursor())
        // La segunda vuelta tiene que pedir desde el último cambio recibido, o volvería a traer
        // la misma página para siempre.
        assertEquals("500", servidor.ultimaPeticion?.url?.queryParameter("desde"))
    }

    @Test
    fun `un cambio local sin subir no lo pisa una bajada mas antigua`() = runBlocking {
        almacen.marcarPendiente(
            Elemento(id = "e1", url = "https://a.com", titulo = "Mío", actualizadoEn = 800)
        )
        responder(
            subida = """{"elementos": [], "rechazados": []}""",
            bajada =
                """{"elementos": [{"id": "e1", "url": "https://a.com", "titulo": "Viejo del servidor",
                   "actualizadoEn": 400}], "servidorEn": 900}""",
        )

        sincronizador.sincronizar()

        assertEquals("Mío", almacen.cargarTodos()["e1"]?.titulo)
    }
}
