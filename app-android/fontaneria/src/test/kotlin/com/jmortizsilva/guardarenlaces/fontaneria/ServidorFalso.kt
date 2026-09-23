package com.jmortizsilva.guardarenlaces.fontaneria

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Un servidor de mentira para probar el cliente entero (cabeceras, cuerpo, códigos de error) sin
 * red y sin backend.
 *
 * En iOS se mete por debajo de `URLSession` y le llega cualquier dirección. Aquí es un servidor
 * HTTP de verdad en `localhost`, y un interceptor de OkHttp le desvía todas las peticiones del
 * cliente de pruebas conservando la ruta: así `https://api.ejemplo.com/auth/canjear` o el oEmbed de
 * YouTube llegan a él sin tocar el código de la app.
 *
 * Uno por prueba, nunca compartido: dos pruebas a la vez se pisarían las respuestas, y el fallo
 * saldría unas veces sí y otras no.
 */
class ServidorFalso : Closeable {
    data class Respuesta(val codigo: Int, val cuerpo: String) {
        companion object {
            fun json(texto: String, codigo: Int = 200) = Respuesta(codigo, texto)
        }
    }

    private val servidor = MockWebServer()
    private val peticiones = CopyOnWriteArrayList<Pedida>()

    /** Lo que se pidió, con la dirección original y no la de `localhost`. */
    class Pedida(val metodo: String, val url: HttpUrl, val cabeceras: Headers, val cuerpo: String) {
        val ruta: String
            get() = url.encodedPath

        val cuerpoJson: JsonObject
            get() = Json.parseToJsonElement(cuerpo).jsonObject
    }

    @Volatile var respuesta = Respuesta.json("{}")

    /** Para contestar distinto según la ruta, o distinto cada vez (renovar y reintentar). */
    @Volatile var manejador: ((Pedida) -> Respuesta)? = null

    @Volatile var fallarLaConexion = false

    val rutasPedidas: List<String>
        get() = peticiones.map { it.ruta }

    val ultimaPeticion: Pedida?
        get() = peticiones.lastOrNull()

    val http: OkHttpClient

    init {
        servidor.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val pedida =
                        Pedida(
                            request.method,
                            requireNotNull(request.headers[CABECERA_ORIGINAL]).toHttpUrl(),
                            request.headers,
                            request.body?.utf8().orEmpty(),
                        )
                    peticiones += pedida
                    val elegida = manejador?.invoke(pedida) ?: respuesta
                    return MockResponse.Builder()
                        .code(elegida.codigo)
                        .setHeader("Content-Type", "application/json; charset=utf-8")
                        .body(elegida.cuerpo)
                        .build()
                }
            }
        servidor.start()
        http =
            OkHttpClient.Builder()
                .addInterceptor { cadena ->
                    // Como quedarse sin red: el fallo sale antes de llegar a ningún servidor.
                    if (fallarLaConexion) throw IOException("sin red, de mentira")
                    val original = cadena.request()
                    val desviada =
                        original.url
                            .newBuilder()
                            .scheme("http")
                            .host(servidor.hostName)
                            .port(servidor.port)
                            .build()
                    cadena.proceed(
                        original
                            .newBuilder()
                            .url(desviada)
                            .header(CABECERA_ORIGINAL, original.url.toString())
                            .build()
                    )
                }
                .build()
    }

    fun cliente(urlBase: String = "https://api.ejemplo.com") = ClienteApi(urlBase, http)

    override fun close() = servidor.close()

    private companion object {
        const val CABECERA_ORIGINAL = "X-Direccion-Original"
    }
}

/**
 * Almacén de credenciales de mentira. El de verdad (`CredencialesKeystore`) necesita el Keystore de
 * un teléfono, así que se prueba en él, con las pruebas instrumentadas.
 */
class CredencialesEnMemoria(tokenInicial: String? = null) : AlmacenCredenciales {
    @Volatile private var token = tokenInicial

    @Volatile
    var escrituras = 0
        private set

    override fun guardarTokenRefresco(token: String) {
        synchronized(this) {
            this.token = token
            escrituras++
        }
    }

    override fun tokenRefresco() = token

    override fun borrarTokenRefresco() {
        token = null
    }
}
