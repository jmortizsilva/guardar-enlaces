package com.jmortizsilva.guardarenlaces

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Las pantallas de la app. */
sealed interface Pantalla {
    data object Lista : Pantalla

    data class Detalle(val id: String) : Pantalla

    data class Etiquetas(val id: String) : Pantalla
}

/**
 * Qué tiene que hacer la pantalla que queda arriba al volver a ella. Con lector de pantalla, volver
 * no es solo enseñar la pantalla anterior: el cursor tiene que acabar donde estaba, o quien la usa
 * pierde el sitio y tiene que buscarlo desde arriba.
 */
sealed interface Llegada {
    /** El cursor a la fila de ese enlace, y el anuncio si lo hay. */
    data class AFila(val id: String, val anuncio: String? = null) : Llegada

    /** Eliminar esa fila con el mismo camino que desde la lista: cursor a la vecina y aviso. */
    data class EliminarFila(val id: String) : Llegada

    /** En el detalle, el cursor al botón de etiquetas, y el anuncio si lo hay. */
    data class ABotonEtiquetas(val anuncio: String? = null) : Llegada
}

/**
 * La pila de pantallas. Una propia en vez de Navigation Compose: son tres pantallas y lo que
 * importa es controlar adónde va el cursor al volver, que Navigation no resuelve.
 */
class Navegacion {
    val pila = mutableStateListOf<Pantalla>(Pantalla.Lista)

    var llegada by mutableStateOf<Llegada?>(null)
        private set

    val actual: Pantalla
        get() = pila.last()

    val puedeVolver: Boolean
        get() = pila.size > 1

    fun abrir(pantalla: Pantalla) {
        llegada = null
        pila.add(pantalla)
    }

    /**
     * Quita la pantalla de arriba. Sin `llegada` explícita, el cursor vuelve a lo que la abrió: la
     * fila del enlace, o en el detalle, el botón de etiquetas.
     */
    fun volver(con: Llegada? = null) {
        if (!puedeVolver) return
        val saliente = pila.removeAt(pila.lastIndex)
        llegada =
            con
                ?: when {
                    actual is Pantalla.Detalle -> Llegada.ABotonEtiquetas()
                    saliente is Pantalla.Detalle -> Llegada.AFila(saliente.id)
                    saliente is Pantalla.Etiquetas -> Llegada.AFila(saliente.id)
                    else -> null
                }
    }

    /** La pantalla que la recibe ya lo ha hecho. */
    fun llegadaAtendida() {
        llegada = null
    }
}
