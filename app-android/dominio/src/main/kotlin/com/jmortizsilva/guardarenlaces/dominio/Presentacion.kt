package com.jmortizsilva.guardarenlaces.dominio

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Los textos con los que se muestra un enlace en la lista.
 *
 * Están aquí, y no en la pantalla, porque son contrato: la fila compone lo que lee TalkBack con
 * ellos, así que el subtítulo tiene que valer igual leído en voz alta que visto. Cambiarlos es
 * cambiar lo que se oye, y por eso tienen pruebas.
 */
object Presentacion {
    /**
     * Español fijo, no el idioma del teléfono: la app está entera en español y una fecha en inglés
     * en mitad de una frase en español se nota más leída que vista.
     *
     * Con `Builder`: `Locale.of` no existe en Android 9, y el constructor está obsoleto en el JDK.
     */
    val localeDeLaApp: Locale = Locale.Builder().setLanguage("es").setRegion("ES").build()

    fun titulo(elemento: Elemento): String = elemento.titulo.orEmpty().ifEmpty { elemento.url }

    fun subtitulo(
        elemento: Elemento,
        locale: Locale = localeDeLaApp,
        zona: ZoneId = ZoneId.systemDefault(),
    ): String {
        val partes = mutableListOf<String>()
        dominio(elemento.url)?.let { partes += it }
        if (elemento.etiquetas.isNotEmpty()) partes += elemento.etiquetas.joinToString(", ")
        // La fecha de guardado, no la de la última modificación: cambiar una etiqueta movía la
        // fecha que se ve, y eso hacía dudar de cuándo se había guardado el enlace de verdad.
        partes += fechaLegible(elemento.creadoEn, locale, zona)
        return partes.joinToString(" — ")
    }

    private fun dominio(url: String): String? = PartesDeUrl.leer(url)?.anfitrion?.ifEmpty { null }

    /**
     * El mes en letra a propósito: «15/3/2024» el lector de pantalla lo lee número a número, y hay
     * que descifrarlo en vez de oírlo.
     */
    fun fechaLegible(instante: MarcaDeTiempo, locale: Locale, zona: ZoneId): String {
        if (instante == 0L) return "sin fecha"
        return Instant.ofEpochMilli(instante)
            .atZone(zona)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }
}
