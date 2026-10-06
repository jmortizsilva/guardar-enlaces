package com.jmortizsilva.guardarenlaces.dominio

/**
 * Qué cuenta como enlace guardable, y cómo sacarlo de lo que llega.
 *
 * Lo usan la pantalla de añadir, donde la dirección se escribe a mano, y la de compartir, donde
 * llega de otra aplicación. Son el mismo criterio y tiene que seguir siéndolo: un enlace que una
 * acepta y la otra rechaza es un fallo que solo se ve al usarlas por separado.
 */
object Enlaces {
    private val esquemas = listOf("https://", "http://")

    /**
     * Se pide `http://` o `https://` por delante y algo detrás.
     *
     * Con criterio de portero y no de analizador: una dirección con un espacio de más o una letra
     * cambiada la rechaza el servidor al pedir los metadatos. Lo que hay que impedir aquí es
     * guardar «nota para el lunes» como si fuera una página.
     */
    fun esDireccion(texto: String): Boolean {
        val limpio = texto.trim()
        val esquema = esquemas.firstOrNull { limpio.startsWith(it) } ?: return false
        return limpio.length > esquema.length
    }

    /**
     * La dirección que haya dentro de un texto compartido, si la hay.
     *
     * Chrome manda la dirección sola, pero muchas aplicaciones comparten una frase con la dirección
     * metida dentro («Mira esto: https://ejemplo.com»). Se parte siempre por los espacios, incluso
     * cuando el texto empieza ya por la dirección: si no, «https://ejemplo.com vía @alguien» se
     * guardaba entero.
     *
     * Por `isWhitespace` y no por la expresión `\s`: en Java, `\s` no incluye el espacio duro, y
     * algunas aplicaciones lo meten justo delante de la dirección.
     */
    fun direccionDentroDe(texto: String): String? =
        partirPorEspacios(texto).firstOrNull { esDireccion(it) }

    /**
     * Lo que se mete en el campo al pegar: la dirección si hay una dentro de lo copiado, y si no,
     * lo copiado tal cual, sin espacios alrededor. Tal cual y no nada: si no es una dirección, el
     * campo dice por qué, y eso se entiende mejor que un botón que no hace nada.
     */
    fun paraPegar(copiado: String): String = direccionDentroDe(copiado) ?: copiado.trim()

    /**
     * La dirección escrita a mano en la pantalla de añadir, con `https://` delante si no lo trae.
     * `null` si lo escrito no es una dirección. Las reglas están en `ANADIR.md`, en la raíz del
     * repositorio, y los casos en `pruebas-compartidas/direcciones/`, que leen las tres apps.
     *
     * Solo para lo escrito a mano: lo compartido sigue pasando por `direccionDentroDe`, que pide el
     * esquema.
     */
    fun completar(texto: String): DireccionEscrita? {
        val limpio = texto.trim()
        val esquema = esquemas.firstOrNull { limpio.startsWith(it, ignoreCase = true) }
        if (esquema != null) {
            return if (limpio.length > esquema.length) DireccionEscrita(limpio, null) else null
        }
        if (!pareceUnSitio(limpio)) return null
        return DireccionEscrita("https://$limpio", "http://$limpio")
    }

    private fun pareceUnSitio(texto: String): Boolean {
        if (texto.isEmpty() || texto.any { it.isWhitespace() } || "://" in texto) return false
        val sitio = texto.takeWhile { it !in "/?#" }
        // Un correo con https:// delante abriría el sitio de detrás de la arroba.
        if ('@' in sitio) return false
        val punto = sitio.indexOf('.')
        return punto > 0 && punto < sitio.length - 1
    }

    private fun partirPorEspacios(texto: String): List<String> {
        val trozos = mutableListOf<String>()
        val actual = StringBuilder()
        for (letra in texto) {
            if (letra.isWhitespace()) {
                if (actual.isNotEmpty()) trozos += actual.toString()
                actual.clear()
            } else {
                actual.append(letra)
            }
        }
        if (actual.isNotEmpty()) trozos += actual.toString()
        return trozos
    }
}

/** Lo que sale de completar una dirección escrita a mano. */
data class DireccionEscrita(
    val direccion: String,
    /**
     * La misma con `http://`, para probarla si la de `https://` no carga. Solo cuando el esquema lo
     * ha puesto la app: si lo escribió quien la usa, se respeta.
     */
    val alternativa: String?,
)
