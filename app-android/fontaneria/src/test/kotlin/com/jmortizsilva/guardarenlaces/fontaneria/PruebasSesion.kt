package com.jmortizsilva.guardarenlaces.fontaneria

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

private const val RESPUESTA_CON_TOKENS =
    """{"tokenAcceso": "acceso-nuevo", "expiraEn": 1735689600000, "tokenRefresco": "refresco-nuevo",
       "usuario": {"id": 1, "email": "persona@ejemplo.com", "proveedor": "google"}}"""

class PruebasSesion {
    private val servidor = ServidorFalso()
    private val cliente = servidor.cliente()

    @AfterTest fun cerrar() = servidor.close()

    private fun sesion(llavero: CredencialesEnMemoria) = Sesion(cliente, llavero)

    /** Una sesión ya restaurada, con token de acceso en la mano. */
    private suspend fun sesionIniciada(llavero: CredencialesEnMemoria): Sesion {
        servidor.respuesta = ServidorFalso.Respuesta.json(RESPUESTA_CON_TOKENS)
        return sesion(llavero).also { it.restaurar() }
    }

    @Test
    fun `sin nada guardado no se molesta ni en preguntar al servidor`() = runBlocking {
        val resultado = sesion(CredencialesEnMemoria()).restaurar()

        assertEquals(Sesion.Restauracion.SinCuenta, resultado)
        assertTrue(servidor.rutasPedidas.isEmpty())
    }

    @Test
    fun `con un token bueno se recupera la sesion y se sabe de quien es`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta.json(RESPUESTA_CON_TOKENS)
        val sesion = sesion(CredencialesEnMemoria("refresco-viejo"))

        assertEquals(Sesion.Restauracion.Restaurada, sesion.restaurar())
        assertTrue(sesion.conCuenta)
        assertEquals("persona@ejemplo.com", sesion.usuario?.email)
    }

    @Test
    fun `el token rotado se guarda, si no el arranque siguiente no entra`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta.json(RESPUESTA_CON_TOKENS)
        val llavero = CredencialesEnMemoria("refresco-viejo")

        sesion(llavero).restaurar()

        assertEquals("refresco-nuevo", llavero.tokenRefresco())
        assertEquals(1, llavero.escrituras)
    }

    @Test
    fun `un token que el servidor rechaza se tira, en vez de reintentarlo cada vez`() =
        runBlocking {
            servidor.respuesta =
                ServidorFalso.Respuesta.json("""{"error": "token revocado"}""", 401)
            val llavero = CredencialesEnMemoria("refresco-viejo")
            val sesion = sesion(llavero)

            assertEquals(Sesion.Restauracion.SinCuenta, sesion.restaurar())
            assertFalse(sesion.conCuenta)
            assertNull(llavero.tokenRefresco())
        }

    @Test
    fun `arrancar sin red no cierra la sesion`() = runBlocking {
        // En iOS, cualquier fallo al renovar tira el token guardado, también no tener conexión.
        // Aquí solo lo tira un 401, que es lo que dice el contrato.
        servidor.fallarLaConexion = true
        val llavero = CredencialesEnMemoria("refresco-bueno")
        val sesion = sesion(llavero)

        val resultado = sesion.restaurar()

        assertIs<Sesion.Restauracion.SinConexion>(resultado)
        assertTrue(sesion.conCuenta)
        assertEquals("refresco-bueno", llavero.tokenRefresco())
    }

    @Test
    fun `con la cuenta y sin token de acceso, la primera peticion lo pide`() = runBlocking {
        // Lo que pasa después de arrancar sin red: al volver la conexión no hay que reiniciar la
        // app para que sincronice.
        val sesion = sesion(CredencialesEnMemoria("refresco-bueno"))
        servidor.fallarLaConexion = true
        sesion.restaurar()
        servidor.fallarLaConexion = false
        servidor.manejador = { pedida ->
            if (pedida.ruta == "/auth/renovar") ServidorFalso.Respuesta.json(RESPUESTA_CON_TOKENS)
            else ServidorFalso.Respuesta.json("""{"titulo": "Ya hay red", "tipo": "enlace"}""")
        }

        val metadatos = sesion.conReintento { token -> cliente.metadatos("https://a.com", token) }

        assertEquals("Ya hay red", metadatos.titulo)
        assertEquals(
            "Bearer acceso-nuevo",
            servidor.ultimaPeticion?.cabeceras?.get("Authorization"),
        )
    }

    @Test
    fun `ante un token de acceso caducado, renueva una vez y lo vuelve a intentar`() = runBlocking {
        val sesion = sesionIniciada(CredencialesEnMemoria("refresco-viejo"))
        val intentos = AtomicInteger()
        servidor.manejador = { pedida ->
            when {
                pedida.ruta == "/auth/renovar" -> ServidorFalso.Respuesta.json(RESPUESTA_CON_TOKENS)
                intentos.incrementAndGet() == 1 ->
                    ServidorFalso.Respuesta.json("""{"error": "token caducado"}""", 401)
                else ->
                    ServidorFalso.Respuesta.json("""{"titulo": "A la segunda", "tipo": "enlace"}""")
            }
        }

        val metadatos = sesion.conReintento { token -> cliente.metadatos("https://a.com", token) }

        assertEquals("A la segunda", metadatos.titulo)
        assertEquals(2, servidor.rutasPedidas.count { it == "/metadatos" })
    }

    @Test
    fun `dos 401 a la vez hacen una sola renovacion`() = runBlocking {
        // Cada renovación revoca el token de refresco usado. Si las dos peticiones renovaran, la
        // segunda lo haría con un token ya revocado y la sesión se cerraría.
        val llavero = CredencialesEnMemoria("refresco-viejo")
        val sesion = sesionIniciada(llavero)
        val renovaciones = AtomicInteger()
        servidor.manejador = { pedida ->
            val autorizacion = pedida.cabeceras["Authorization"]
            when {
                pedida.ruta == "/auth/renovar" -> {
                    renovaciones.incrementAndGet()
                    Thread.sleep(200)
                    ServidorFalso.Respuesta.json(
                        """{"tokenAcceso": "acceso-3", "expiraEn": 1, "tokenRefresco": "refresco-3"}"""
                    )
                }
                autorizacion == "Bearer acceso-3" ->
                    ServidorFalso.Respuesta.json("""{"titulo": "Bien", "tipo": "enlace"}""")
                else -> ServidorFalso.Respuesta.json("""{"error": "token caducado"}""", 401)
            }
        }
        val antes = servidor.rutasPedidas.count { it == "/auth/renovar" }

        val resultados =
            (1..2)
                .map {
                    async(Dispatchers.IO) {
                        sesion.conReintento { token -> cliente.metadatos("https://a.com", token) }
                    }
                }
                .awaitAll()

        assertEquals(listOf("Bien", "Bien"), resultados.map { it.titulo })
        assertEquals(1, servidor.rutasPedidas.count { it == "/auth/renovar" } - antes)
        assertEquals("refresco-3", llavero.tokenRefresco())
    }

    @Test
    fun `si la renovacion tambien falla, el fallo llega a la pantalla`() = runBlocking {
        val sesion = sesionIniciada(CredencialesEnMemoria("refresco-viejo"))
        servidor.respuesta = ServidorFalso.Respuesta.json("""{"error": "sesión no válida"}""", 401)

        assertFailsWith<ErrorApi> {
            sesion.conReintento { token -> cliente.metadatos("https://a.com", token) }
        }
        assertFalse(sesion.conCuenta)
    }

    @Test
    fun `sin sesion iniciada no se inventa una peticion sin token`() = runBlocking {
        val sesion = sesion(CredencialesEnMemoria())

        assertFailsWith<ErrorApi> {
            sesion.conReintento { token -> cliente.metadatos("https://a.com", token) }
        }
        assertTrue(servidor.rutasPedidas.isEmpty())
    }

    @Test
    fun `cerrar sesion borra el token aunque el servidor no conteste`() = runBlocking {
        val llavero = CredencialesEnMemoria("refresco-viejo")
        val sesion = sesionIniciada(llavero)

        servidor.fallarLaConexion = true
        sesion.cerrar()

        assertNull(llavero.tokenRefresco())
        assertFalse(sesion.conCuenta)
        assertNull(sesion.tokenAcceso)
    }

    @Test
    fun `entrar con el codigo del navegador deja la sesion guardada`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta.json(RESPUESTA_CON_TOKENS)
        val llavero = CredencialesEnMemoria()
        val sesion = sesion(llavero)

        sesion.entrar("codigo-de-un-solo-uso")

        assertTrue(sesion.conCuenta)
        assertEquals("refresco-nuevo", llavero.tokenRefresco())
        assertEquals("/auth/canjear", servidor.ultimaPeticion?.ruta)
    }
}
