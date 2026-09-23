package com.jmortizsilva.guardarenlaces

import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.TipoElemento
import com.jmortizsilva.guardarenlaces.fontaneria.AlmacenLocal

/**
 * Enlaces para oír la lista antes de que exista la pantalla de añadir. Solo en la versión de
 * pruebas (la de publicación tiene uno vacío en `src/release`), y solo si se piden al abrir, con
 * `./instalar --ejemplos`.
 *
 * Repone los cuatro por su identificador, también si se habían eliminado, para poder repetir la
 * prueba de eliminar. Ningún otro enlace se toca.
 */
object Ejemplos {
    const val EXTRA = "cargar-ejemplos"

    /** Mediodía UTC del 15 de marzo de 2024, la misma fecha que los ejemplos de iOS. */
    private const val QUINCE_DE_MARZO = 1_710_504_000_000L
    private const val UN_DIA = 86_400_000L

    fun reponer(almacen: AlmacenLocal) {
        val todos =
            listOf(
                Elemento(
                    id = "ejemplo-1",
                    url = "https://www.xataka.com/basics/alternativas-pocket",
                    titulo = "Las mejores alternativas a Pocket",
                    descripcion = "Pocket cierra y estas son las opciones",
                    tipo = TipoElemento.Articulo,
                    etiquetas = listOf("ocio", "pendiente"),
                    creadoEn = QUINCE_DE_MARZO,
                    actualizadoEn = QUINCE_DE_MARZO,
                ),
                Elemento(
                    id = "ejemplo-2",
                    url = "https://developer.android.com/guide/topics/ui/accessibility",
                    titulo = "Accesibilidad en Android",
                    etiquetas = listOf("trabajo"),
                    creadoEn = QUINCE_DE_MARZO - UN_DIA,
                    actualizadoEn = QUINCE_DE_MARZO - UN_DIA,
                ),
                Elemento(
                    id = "ejemplo-3",
                    url = "https://www.youtube.com/watch?v=abc123",
                    titulo = "Cómo funciona TalkBack",
                    tipo = TipoElemento.Video,
                    etiquetas = listOf("pendiente"),
                    creadoEn = QUINCE_DE_MARZO - 2 * UN_DIA,
                    actualizadoEn = QUINCE_DE_MARZO - 2 * UN_DIA,
                ),
                Elemento(
                    id = "ejemplo-4",
                    url = "https://es.wikipedia.org/wiki/Lector_de_pantalla",
                    creadoEn = QUINCE_DE_MARZO - 3 * UN_DIA,
                    actualizadoEn = QUINCE_DE_MARZO - 3 * UN_DIA,
                ),
            )
        almacen.guardar(todos.associateBy { it.id })
    }
}
