package com.jmortizsilva.guardarenlaces.dominio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

data class MetadatosExtraidos(
    val titulo: String? = null,
    val descripcion: String? = null,
    val imagenUrl: String? = null,
    val tipo: TipoElemento = TipoElemento.Enlace,
)

/**
 * Lee los metadatos Open Graph de un HTML ya descargado: un par de expresiones regulares
 * tolerantes, sin librería de análisis.
 *
 * Esto es una COPIA de `backend/src/metadatos/extraccion.ts`, igual que la de iOS. Los proyectos
 * del repositorio no comparten código (ver PROYECTO.md), así que se duplica a propósito: si cambia
 * el criterio de qué es un vídeo o un artículo, hay que tocarlo EN TODOS LOS SITIOS, o el mismo
 * enlace saldrá distinto según se guarde con cuenta (lo resuelve el servidor) o sin ella (lo
 * resuelve el teléfono).
 */
object Metadatos {
    /**
     * Solo estas cinco, las mismas que el backend. `&eacute;` y compañía se quedan sin traducir, y
     * eso se ve en el título. No se arregla aquí a solas: hacerlo solo en el teléfono es justo lo
     * que haría que el mismo enlace se viera distinto según quién resolvió sus metadatos.
     */
    private val entidades =
        listOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'")

    private fun decodificarEntidades(texto: String): String =
        entidades.fold(texto) { resultado, (entidad, letra) -> resultado.replace(entidad, letra) }

    private fun primerGrupo(patron: String, texto: String): String? =
        Regex(patron, RegexOption.IGNORE_CASE).find(texto)?.groupValues?.get(1)

    /**
     * El orden de `property` y `content` cambia según el sitio, así que se prueban las dos formas.
     */
    private fun metaOpenGraph(html: String, propiedad: String): String? {
        val patrones =
            listOf(
                """<meta[^>]+property=["']og:$propiedad["'][^>]+content=["']([^"']*)["']""",
                """<meta[^>]+content=["']([^"']*)["'][^>]+property=["']og:$propiedad["']""",
            )
        return patrones.firstNotNullOfOrNull { primerGrupo(it, html) }?.let(::decodificarEntidades)
    }

    private fun tituloDeLaPestana(html: String): String? {
        val bruto = primerGrupo("<title[^>]*>([^<]*)</title>", html) ?: return null
        return decodificarEntidades(bruto).trim().ifEmpty { null }
    }

    private fun tipoSegunOpenGraph(ogType: String?): TipoElemento =
        when {
            ogType == null -> TipoElemento.Enlace
            "video" in ogType -> TipoElemento.Video
            "article" in ogType -> TipoElemento.Articulo
            "image" in ogType || "photo" in ogType -> TipoElemento.Imagen
            else -> TipoElemento.Enlace
        }

    fun extraer(html: String) =
        MetadatosExtraidos(
            titulo = metaOpenGraph(html, "title") ?: tituloDeLaPestana(html),
            descripcion = metaOpenGraph(html, "description"),
            imagenUrl = metaOpenGraph(html, "image"),
            tipo = tipoSegunOpenGraph(metaOpenGraph(html, "type")),
        )
}

/**
 * YouTube aparte: su oEmbed público da título y miniatura más fiables que raspar `og:*`, y no
 * necesita credenciales. COPIA de `backend/src/metadatos/youtube.ts` (ver la nota de arriba).
 */
object Youtube {
    private val dominios = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

    fun esUrlDeYoutube(url: String): Boolean =
        PartesDeUrl.leer(url)?.anfitrion?.lowercase() in dominios

    /**
     * La dirección se escapa entera y a mano: pasan solo letras, números y `-._~`, igual que en
     * iOS. El backend usa `encodeURIComponent`, que deja pasar además `!*'()`; para YouTube es la
     * misma dirección, así que no se copia esa diferencia. `URLEncoder` de Java no vale: es para
     * formularios y convierte los espacios en `+`.
     */
    fun urlOEmbed(url: String): String =
        "https://www.youtube.com/oembed?url=${escapar(url)}&format=json"

    private fun escapar(texto: String): String = buildString {
        for (byte in texto.toByteArray(Charsets.UTF_8)) {
            val caracter = byte.toInt().toChar()
            if (byte >= 0 && (caracter.isLetterOrDigit() || caracter in "-._~")) {
                append(caracter)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    @Serializable
    private class RespuestaOEmbed(
        val title: String? = null,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    )

    /**
     * Lee la respuesta del oEmbed. Si no se entiende, devuelve null y quien llama cae al raspado
     * genérico, que es lo que hace el backend.
     */
    fun metadatos(desdeOEmbed: String): MetadatosExtraidos? {
        val respuesta =
            try {
                jsonDelContrato.decodeFromString<RespuestaOEmbed>(desdeOEmbed)
            } catch (_: SerializationException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
        return MetadatosExtraidos(
            titulo = respuesta.title,
            imagenUrl = respuesta.thumbnailUrl,
            tipo = TipoElemento.Video,
        )
    }
}
