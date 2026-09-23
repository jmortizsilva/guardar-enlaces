package com.jmortizsilva.guardarenlaces.fontaneria

import com.jmortizsilva.guardarenlaces.dominio.TipoElemento
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PruebasResolverMetadatos {
    private val servidor = ServidorFalso()
    private val cliente = servidor.cliente()

    @AfterTest fun cerrar() = servidor.close()

    private suspend fun resolvedor(conCuenta: Boolean): ResolverMetadatos {
        val sesion = Sesion(cliente, CredencialesEnMemoria(if (conCuenta) "refresco" else null))
        if (conCuenta) {
            servidor.respuesta =
                ServidorFalso.Respuesta.json(
                    """{"tokenAcceso": "acceso", "expiraEn": 1, "tokenRefresco": "r2"}"""
                )
            sesion.restaurar()
        }
        return ResolverMetadatos(cliente, sesion, servidor.http)
    }

    @Test
    fun `con cuenta los pide al servidor, que es quien los guarda para todos`() = runBlocking {
        val resolvedor = resolvedor(conCuenta = true)
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"titulo": "Lo dijo el servidor", "descripcion": null, "imagenUrl": null,
                   "tipo": "articulo"}"""
            )

        val metadatos = resolvedor.resolver("https://a.com")

        assertEquals("Lo dijo el servidor", metadatos?.titulo)
        assertEquals(TipoElemento.Articulo, metadatos?.tipo)
        assertTrue("/metadatos" in servidor.rutasPedidas)
    }

    @Test
    fun `sin cuenta descarga la pagina el propio telefono`() = runBlocking {
        val resolvedor = resolvedor(conCuenta = false)
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                "<html><head><title>Lo leyó el teléfono</title></head></html>"
            )

        val metadatos = resolvedor.resolver("https://a.com/articulo")

        assertEquals("Lo leyó el teléfono", metadatos?.titulo)
        // Nunca pasa por el servidor: sin cuenta no hay a quién preguntar.
        assertFalse("/metadatos" in servidor.rutasPedidas)
        assertEquals("https://a.com/articulo", servidor.ultimaPeticion?.url.toString())
    }

    @Test
    fun `un video de YouTube se resuelve por su oEmbed, no raspando la pagina`() = runBlocking {
        val resolvedor = resolvedor(conCuenta = false)
        servidor.respuesta =
            ServidorFalso.Respuesta.json(
                """{"title": "Un vídeo", "thumbnail_url": "https://i.ytimg.com/a.jpg"}"""
            )

        val metadatos = resolvedor.resolver("https://youtu.be/abc123")

        assertEquals("Un vídeo", metadatos?.titulo)
        assertEquals(TipoElemento.Video, metadatos?.tipo)
        assertEquals("www.youtube.com", servidor.ultimaPeticion?.url?.host)
        assertEquals("/oembed", servidor.ultimaPeticion?.ruta)
    }

    @Test
    fun `si el oEmbed falla, cae a leer la pagina`() = runBlocking {
        val resolvedor = resolvedor(conCuenta = false)
        servidor.manejador = { pedida ->
            if (pedida.ruta == "/oembed") ServidorFalso.Respuesta(404, "")
            else ServidorFalso.Respuesta.json("<title>La página del vídeo</title>")
        }

        assertEquals("La página del vídeo", resolvedor.resolver("https://youtu.be/abc123")?.titulo)
    }

    @Test
    fun `si no se puede comprobar, no se inventa nada ni se lanza un error`() = runBlocking {
        val resolvedor = resolvedor(conCuenta = false)
        servidor.fallarLaConexion = true

        // Guardar el enlace no puede depender de esto, así que aquí se devuelve que no se sabe.
        assertNull(resolvedor.resolver("https://a.com"))
    }

    @Test
    fun `una pagina que responde con error tampoco da metadatos inventados`() = runBlocking {
        val resolvedor = resolvedor(conCuenta = false)
        servidor.respuesta = ServidorFalso.Respuesta(403, "<html>no autorizado</html>")

        assertNull(resolvedor.resolver("https://a.com"))
    }

    @Test
    fun `de una pagina enorme solo se lee el principio, que es donde esta el titulo`() =
        runBlocking {
            val resolvedor = resolvedor(conCuenta = false)
            servidor.respuesta =
                ServidorFalso.Respuesta.json(
                    "<title>Cabecera</title>" + "x".repeat(3 * 1024 * 1024)
                )

            assertEquals("Cabecera", resolvedor.resolver("https://a.com")?.titulo)
        }

    @Test
    fun `con cuenta y sin red, no se sabe, y ya esta`() = runBlocking {
        val resolvedor = resolvedor(conCuenta = true)
        servidor.fallarLaConexion = true

        assertNull(resolvedor.resolver("https://a.com"))
    }
}
