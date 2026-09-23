package com.jmortizsilva.guardarenlaces.fontaneria

import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.Proveedor
import com.jmortizsilva.guardarenlaces.dominio.TipoElemento
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

class PruebasClienteApi {
    private val servidor = ServidorFalso()
    private val cliente = servidor.cliente()

    @AfterTest fun cerrar() = servidor.close()

    @Test
    fun `la barra final de la direccion no se cuela en las rutas`() = runBlocking {
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"tokenAcceso": "a", "expiraEn": 1, "tokenRefresco": "r"}"""
            )

        servidor.cliente("https://api.ejemplo.com///").canjear("c")

        assertEquals(
            "https://api.ejemplo.com/auth/canjear",
            servidor.ultimaPeticion?.url.toString(),
        )
    }

    @Test
    fun `la direccion del login lleva los cuatro parametros del contrato`() {
        val url = cliente.urlIniciarLogin(Proveedor.Apple, estado = "abc123").toHttpUrl()

        assertEquals("/auth/iniciar", url.encodedPath)
        assertEquals("apple", url.queryParameter("proveedor"))
        assertEquals("deeplink", url.queryParameter("modo"))
        assertEquals("abc123", url.queryParameter("estado"))
        assertEquals("guardarenlaces", url.queryParameter("esquema"))
    }

    @Test
    fun `canjear devuelve los tokens y con quien se ha entrado`() = runBlocking {
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"tokenAcceso": "acceso", "expiraEn": 1735689600000, "tokenRefresco": "refresco",
                   "usuario": {"id": 1, "email": "persona@ejemplo.com", "proveedor": "google"}}"""
            )

        val respuesta = cliente.canjear("codigo")

        assertEquals("acceso", respuesta.tokenAcceso)
        assertEquals("refresco", respuesta.tokenRefresco)
        assertEquals("persona@ejemplo.com", respuesta.usuario?.email)
        assertEquals(
            "codigo",
            servidor.ultimaPeticion?.cuerpoJson?.get("codigoCanje")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `renovar sin usuario tambien vale, no todas las respuestas lo traen`() = runBlocking {
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"tokenAcceso": "a2", "expiraEn": 2, "tokenRefresco": "r2"}"""
            )

        val respuesta = cliente.renovar("r1")

        assertNull(respuesta.usuario)
        assertEquals("r2", respuesta.tokenRefresco)
    }

    @Test
    fun `las llamadas con sesion mandan el token en la cabecera`() = runBlocking {
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"titulo": "T", "descripcion": null, "imagenUrl": null, "tipo": "articulo"}"""
            )

        val metadatos = cliente.metadatos("https://a.com", "elToken")

        assertEquals("Bearer elToken", servidor.ultimaPeticion?.cabeceras?.get("Authorization"))
        assertEquals(TipoElemento.Articulo, metadatos.comoMetadatos.tipo)
    }

    @Test
    fun `un 401 se reconoce como sesion caducada`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta.json("""{"error": "token caducado"}""", 401)

        val fallo = assertFailsWith<ErrorApi> { cliente.metadatos("https://a.com", "viejo") }

        assertTrue(fallo.esSesionCaducada)
        assertEquals("token caducado", fallo.mensaje)
    }

    @Test
    fun `un error sin cuerpo del contrato dice el codigo`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta(502, "<html>Bad Gateway</html>")

        val fallo = assertFailsWith<ErrorApi> { cliente.metadatos("https://a.com", "t") }

        assertEquals("el servidor respondió con un error 502", fallo.mensaje)
    }

    @Test
    fun `sin conexion, el aviso esta en castellano y no habla de sockets`() = runBlocking {
        servidor.fallarLaConexion = true

        val fallo = assertFailsWith<ErrorApi> { cliente.canjear("c") }

        assertEquals("sin conexión con el servidor", fallo.mensaje)
        assertNull(fallo.codigo)
    }

    @Test
    fun `una respuesta que no se entiende se dice, no revienta`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta.json("esto no es json")

        val fallo = assertFailsWith<ErrorApi> { cliente.canjear("c") }

        assertEquals("el servidor respondió algo que no se entiende", fallo.mensaje)
    }

    @Test
    fun `bajar cambios entiende enlaces y etiquetas en la misma respuesta`() = runBlocking {
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"elementos": [{"id": "e1", "url": "https://a.com", "actualizadoEn": 100}],
                   "etiquetasDefinidas": [{"id": "t1", "nombre": "ocio", "actualizadoEn": 90}],
                   "servidorEn": 1735600000123, "masDisponible": true}"""
            )

        val respuesta = cliente.bajarCambios(desde = 50, tokenAcceso = "t")

        assertEquals(listOf("e1"), respuesta.elementos.map { it.id })
        assertEquals(listOf("ocio"), respuesta.etiquetasDefinidas.map { it.nombre })
        assertEquals(1_735_600_000_123, respuesta.servidorEn)
        assertTrue(respuesta.masDisponible)
        assertEquals("50", servidor.ultimaPeticion?.url?.queryParameter("desde"))
        assertEquals("GET", servidor.ultimaPeticion?.metodo)
    }

    @Test
    fun `una respuesta sin etiquetas no rompe nada`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta.json("""{"elementos": [], "servidorEn": 5}""")

        val respuesta = cliente.bajarCambios(desde = 0, tokenAcceso = "t")

        assertTrue(respuesta.etiquetasDefinidas.isEmpty())
        assertFalse(respuesta.masDisponible)
    }

    @Test
    fun `al subir solo se manda lo que hay, no listas vacias`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta.json("""{"elementos": [], "rechazados": []}""")

        cliente.subirCambios(
            elementos = listOf(Elemento(id = "e1", url = "https://a.com", actualizadoEn = 100)),
            etiquetas = emptyList(),
            tokenAcceso = "t",
        )

        val cuerpo = servidor.ultimaPeticion!!.cuerpoJson
        assertTrue("elementos" in cuerpo)
        assertFalse("etiquetasDefinidas" in cuerpo)
    }

    @Test
    fun `lo rechazado llega con su motivo, para poder sacarlo de la cola`() = runBlocking {
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"elementos": [], "rechazados": [{"id": "e1", "motivo": "no_aplicable"}],
                   "etiquetasRechazadas": [{"id": "t1", "motivo": "sin_nombre"}]}"""
            )

        val respuesta =
            cliente.subirCambios(
                listOf(Elemento(id = "e1", url = "https://a.com")),
                emptyList(),
                "t",
            )

        assertEquals(listOf(EntradaRechazada("e1", "no_aplicable")), respuesta.rechazados)
        assertEquals(listOf("t1"), respuesta.etiquetasRechazadas.map { it.id })
    }

    @Test
    fun `cerrar sesion aguanta que el servidor no conteste nada`() = runBlocking {
        servidor.respuesta = ServidorFalso.Respuesta(200, "")

        cliente.cerrarSesion("r")

        assertEquals("/auth/logout", servidor.ultimaPeticion?.ruta)
    }
}
