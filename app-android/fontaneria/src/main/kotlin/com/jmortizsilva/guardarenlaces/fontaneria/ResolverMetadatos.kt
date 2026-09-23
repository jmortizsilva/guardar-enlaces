package com.jmortizsilva.guardarenlaces.fontaneria

import com.jmortizsilva.guardarenlaces.dominio.Metadatos
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.Youtube
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * De dónde salen el título y la descripción de una URL.
 *
 * Con cuenta los saca el servidor, que es quien los guarda para todos los clientes; sin cuenta, el
 * propio teléfono descarga la página y la lee.
 *
 * Lo que aquí NO hace falta, y en el servidor sí: comprobar que la URL no apunta a una dirección de
 * red privada. Allí es imprescindible porque el servidor descarga una URL que le manda otro y
 * podría alcanzar su red interna; aquí la descarga el propio teléfono, con la URL que ha escrito su
 * dueño, y no llega a ningún sitio al que no llegase ya el navegador.
 */
class ResolverMetadatos(
    private val cliente: ClienteApi,
    private val sesion: Sesion,
    private val http: OkHttpClient = clienteHttpPorDefecto,
) {
    /** Devuelve `null` si no se pudo averiguar nada. No lanza: guardar nunca depende de esto. */
    suspend fun resolver(url: String): MetadatosExtraidos? =
        if (sesion.conCuenta) enElServidor(url) else enElTelefono(url)

    private suspend fun enElServidor(url: String): MetadatosExtraidos? =
        try {
            sesion.conReintento { token -> cliente.metadatos(url, token).comoMetadatos }
        } catch (_: ErrorApi) {
            null
        }

    private suspend fun enElTelefono(url: String): MetadatosExtraidos? {
        // Mismo orden que el servidor: YouTube por su oEmbed, que da título y miniatura más
        // fiables que raspar og:*, y si falla, la página entera.
        if (Youtube.esUrlDeYoutube(url)) {
            descargar(Youtube.urlOEmbed(url), Long.MAX_VALUE)?.let(Youtube::metadatos)?.let {
                return it
            }
        }
        return descargar(url, limiteDePagina)?.let(Metadatos::extraer)
    }

    /**
     * Sin identificarse como nada en particular: algunos sitios responden un HTML distinto, o un
     * muro, a lo que parece un robot, y aquí interesa justo lo que vería el navegador.
     */
    private suspend fun descargar(url: String, limite: Long): String? {
        val direccion = url.toHttpUrlOrNull() ?: return null
        val (codigo, cuerpo) =
            try {
                http.newCall(Request.Builder().url(direccion).get().build()).ejecutar(limite)
            } catch (_: IOException) {
                return null
            }
        return cuerpo.takeIf { codigo in 200..299 }
    }

    private companion object {
        /**
         * El título y los `og:*` están en la cabecera de la página. Leer entera una página de
         * varios megas para eso gasta datos y memoria sin ganar nada. En iOS no hay límite.
         */
        const val limiteDePagina = 512L * 1024
    }
}
