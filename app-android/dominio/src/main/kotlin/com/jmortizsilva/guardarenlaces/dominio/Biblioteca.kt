package com.jmortizsilva.guardarenlaces.dominio

import java.text.Collator

/**
 * Consultas y reorganizaciones sobre los enlaces ya guardados: buscar, filtrar, contar y renombrar
 * etiquetas. Separado de `Sincronizacion`, como en iOS: aquí solo se mira y se reordena lo que ya
 * hay, y fusionar con el servidor es otro motivo para cambiar el código.
 */
object Biblioteca {
    /** Filtro local sobre título, URL y etiquetas, sin distinguir mayúsculas. */
    fun buscar(elementos: List<Elemento>, texto: String): List<Elemento> {
        val consulta = texto.trim().lowercase()
        if (consulta.isEmpty()) return elementos
        return elementos.filter { elemento ->
            elemento.titulo.orEmpty().lowercase().contains(consulta) ||
                elemento.url.lowercase().contains(consulta) ||
                elemento.etiquetas.any { it.lowercase().contains(consulta) }
        }
    }

    /** Las etiquetas que de verdad lleva algún enlace, ordenadas. Para el filtro de la lista. */
    fun etiquetasEnUso(elementos: List<Elemento>): List<String> =
        elementos.flatMap { it.etiquetas }.toSet().sortedWith(ordenAlfabetico)

    /**
     * Las que se pueden elegir: las que ya lleva algún enlace más las reservadas, que existen
     * aunque todavía no las lleve ninguno. Sin repetir y en orden alfabético.
     */
    fun etiquetasDisponibles(elementos: List<Elemento>, reservadas: List<String>): List<String> =
        (elementos.flatMap { it.etiquetas } + reservadas).toSet().sortedWith(ordenAlfabetico)

    /** Cuántos enlaces lleva cada etiqueta, para poder decirlo antes de renombrarla o borrarla. */
    fun recuentoPorEtiqueta(elementos: List<Elemento>): Map<String, Int> =
        elementos.flatMap { it.etiquetas }.groupingBy { it }.eachCount()

    /** Sin etiqueta (nula o vacía) no filtra nada. */
    fun filtrarPorEtiqueta(elementos: List<Elemento>, etiqueta: String?): List<Elemento> {
        if (etiqueta.isNullOrEmpty()) return elementos
        return elementos.filter { etiqueta in it.etiquetas }
    }

    /**
     * Los elementos que llevaban `vieja`, con esa etiqueta cambiada por `nueva`. Devuelve solo los
     * que cambian, que son los que hay que subir.
     *
     * Si un elemento ya tenía las dos, no se queda con la etiqueta repetida: dos etiquetas
     * fundiéndose en una es un resultado válido, no un error.
     */
    fun renombrarEtiqueta(
        en: List<Elemento>,
        vieja: String,
        nueva: String,
        ahora: Reloj = ::relojDelSistema,
    ): List<Elemento> =
        en.filter { vieja in it.etiquetas }
            .map { elemento ->
                elemento.conEtiquetas(
                    elemento.etiquetas.map { if (it == vieja) nueva else it }.distinct(),
                    ahora,
                )
            }

    /** Los elementos que llevaban `etiqueta`, sin ella. Solo los que cambian. */
    fun quitarEtiqueta(
        en: List<Elemento>,
        etiqueta: String,
        ahora: Reloj = ::relojDelSistema,
    ): List<Elemento> =
        en.filter { etiqueta in it.etiquetas }
            .map { it.conEtiquetas(it.etiquetas.filter { nombre -> nombre != etiqueta }, ahora) }
}

/**
 * Orden alfabético en español, el que usa `localizedCompare` en iOS: «ábaco» va junto a «abeja» y
 * no detrás de la zeta, que es donde lo dejaría comparar los códigos de las letras.
 */
internal val ordenAlfabetico: Comparator<String> =
    Collator.getInstance(Presentacion.localeDeLaApp).let { cotejo ->
        Comparator { una, otra -> cotejo.compare(una, otra) }
    }
