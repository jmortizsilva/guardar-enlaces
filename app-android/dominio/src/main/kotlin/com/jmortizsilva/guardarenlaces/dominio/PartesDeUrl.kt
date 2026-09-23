package com.jmortizsilva.guardarenlaces.dominio

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Lo que el dominio necesita de una URL: el anfitrión, la ruta y los parámetros. Hace el papel de
 * `URLComponents` en iOS.
 *
 * Se usa `java.net.URI` y no `android.net.Uri` porque este módulo no sabe nada de Android y se
 * prueba en la JVM. `URI` es estricto: rechaza una dirección con espacios, por ejemplo. Quien lo
 * usa lo trata como «no se sabe» y sigue, que es lo que hace iOS cuando `URLComponents` devuelve
 * `nil`.
 */
internal class PartesDeUrl
private constructor(
    val anfitrion: String?,
    val ruta: String,
    val parametros: List<Pair<String, String?>>,
) {
    companion object {
        fun leer(texto: String): PartesDeUrl? {
            val uri =
                try {
                    URI(texto)
                } catch (_: URISyntaxException) {
                    return null
                }
            return PartesDeUrl(uri.host, uri.path.orEmpty(), leerParametros(uri.rawQuery))
        }

        /**
         * Se parte la consulta sin decodificar y se decodifica cada trozo después: si se
         * decodificara antes, un `&` escapado dentro de un valor partiría el parámetro en dos. Un
         * parámetro sin `=` tiene valor nulo, como en `URLComponents`.
         */
        private fun leerParametros(consulta: String?): List<Pair<String, String?>> {
            if (consulta.isNullOrEmpty()) return emptyList()
            return consulta.split('&').map { parametro ->
                val igual = parametro.indexOf('=')
                if (igual < 0) {
                    decodificar(parametro) to null
                } else {
                    decodificar(parametro.substring(0, igual)) to
                        decodificar(parametro.substring(igual + 1))
                }
            }
        }

        /**
         * `URLDecoder` es para formularios y convierte el `+` en espacio. En una URL, `+` es un
         * `+`, y así lo lee `URLComponents`; se protege antes de decodificar.
         */
        private fun decodificar(texto: String): String =
            try {
                // Con el nombre y no con `Charsets.UTF_8`: esa forma es de la API 33.
                URLDecoder.decode(texto.replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                texto
            }
    }
}
