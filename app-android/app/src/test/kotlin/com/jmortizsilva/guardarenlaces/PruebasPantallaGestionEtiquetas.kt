package com.jmortizsilva.guardarenlaces

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.jmortizsilva.guardarenlaces.dominio.Textos
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Como las de la lista: el árbol de semántica y adónde se pide llevar el cursor. Lo que se oye de
 * verdad se comprueba en el teléfono.
 */
@RunWith(RobolectricTestRunner::class)
class PruebasPantallaGestionEtiquetas {
    @get:Rule val compose = createComposeRule()

    private val anuncios = Anuncios()
    private val cursor = mutableListOf<String>()
    private val creadas = mutableListOf<String>()

    /** Imita al modelo: renombrar y eliminar cambian la lista y devuelven lo que hay que decir. */
    private fun mostrar(
        iniciales: List<Pair<String, Int>> = listOf("casa" to 2, "ocio" to 1, "viajes" to 0)
    ): MutableList<Pair<String, Int>> {
        val etiquetas = mutableStateListOf(*iniciales.toTypedArray())
        compose.setContent {
            CompositionLocalProvider(
                LocalMovedorDeCursor provides MovedorDeCursor { cursor += it }
            ) {
                PantallaGestionEtiquetas(
                    etiquetas = etiquetas.toList(),
                    anuncios = anuncios,
                    alVolver = {},
                    crear = { nombre ->
                        creadas += nombre
                        Textos.etiquetaAnadida(nombre.trim())
                    },
                    renombrar = { vieja, nueva ->
                        val i = etiquetas.indexOfFirst { it.first == vieja }
                        val enlaces = etiquetas[i].second
                        etiquetas[i] = nueva to enlaces
                        Textos.etiquetaRenombrada(vieja, nueva, enlaces)
                    },
                    eliminar = { nombre ->
                        val enlaces = etiquetas.first { it.first == nombre }.second
                        etiquetas.removeAll { it.first == nombre }
                        Textos.etiquetaEliminada(nombre, enlaces)
                    },
                )
            }
        }
        return etiquetas
    }

    private fun fila(nombre: String, enlaces: Int) =
        compose.onNodeWithContentDescription(Textos.etiquetaConRecuento(nombre, enlaces))

    private fun esperarAlCursorYAlAviso() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun el_titulo_de_la_pantalla_es_un_encabezado() {
        mostrar()
        compose
            .onNodeWithText(Textos.gestionarEtiquetas)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun cada_fila_dice_su_recuento_y_tiene_renombrar_y_eliminar_en_ese_orden() {
        mostrar()

        val acciones =
            fila("casa", 2).fetchSemanticsNode().config[SemanticsActions.CustomActions].map {
                it.label
            }
        assertEquals(listOf(Textos.renombrar, Textos.eliminar), acciones)
        fila("viajes", 0).assertExists()
    }

    @Test
    fun eliminar_pregunta_antes_diciendo_a_cuantos_enlaces_afecta() {
        mostrar()

        fila("casa", 2).performCustomAction(Textos.eliminar)

        compose.onNodeWithText(Textos.preguntaEliminarEtiqueta("casa", 2)).assertExists()
    }

    @Test
    fun al_eliminar_el_cursor_va_a_la_siguiente_y_se_dice_el_resultado() {
        val etiquetas = mostrar()

        fila("casa", 2).performCustomAction(Textos.eliminar)
        compose.onNode(hasText(Textos.eliminar) and hasClickAction()).performClick()
        esperarAlCursorYAlAviso()

        assertEquals(listOf("ocio", "viajes"), etiquetas.map { it.first })
        assertEquals(etiquetaDeGestion("ocio"), cursor.last())
        assertEquals("Etiqueta «casa» eliminada de 2 enlaces", anuncios.actual.value?.texto)
    }

    @Test
    fun al_eliminar_la_ultima_el_cursor_va_a_la_anterior() {
        mostrar()

        fila("viajes", 0).performCustomAction(Textos.eliminar)
        compose.onNode(hasText(Textos.eliminar) and hasClickAction()).performClick()
        esperarAlCursorYAlAviso()

        assertEquals(etiquetaDeGestion("ocio"), cursor.last())
    }

    @Test
    fun al_eliminar_la_unica_el_cursor_va_al_campo_de_crear() {
        mostrar(listOf("casa" to 1))

        fila("casa", 1).performCustomAction(Textos.eliminar)
        compose.onNode(hasText(Textos.eliminar) and hasClickAction()).performClick()
        esperarAlCursorYAlAviso()

        assertEquals(ETIQUETA_NUEVA_ETIQUETA, cursor.last())
    }

    @Test
    fun cancelar_devuelve_el_cursor_a_la_misma_fila_y_no_dice_nada() {
        val etiquetas = mostrar()

        fila("ocio", 1).performCustomAction(Textos.eliminar)
        compose.onNodeWithText(Textos.cancelar).performClick()
        esperarAlCursorYAlAviso()

        assertEquals(3, etiquetas.size)
        assertEquals(etiquetaDeGestion("ocio"), cursor.last())
        assertEquals(null, anuncios.actual.value)
    }

    @Test
    fun al_renombrar_el_cursor_va_a_la_fila_con_el_nombre_nuevo_y_se_dice() {
        val etiquetas = mostrar()

        fila("ocio", 1).performCustomAction(Textos.renombrar)
        compose.onNodeWithText(Textos.nuevoNombre).performTextReplacement("tiempo libre")
        compose.onNodeWithText(Textos.guardar).performClick()
        esperarAlCursorYAlAviso()

        assertEquals("tiempo libre" to 1, etiquetas[1])
        assertEquals(etiquetaDeGestion("tiempo libre"), cursor.last())
        assertEquals(
            "Etiqueta «ocio» renombrada a «tiempo libre» en 1 enlace",
            anuncios.actual.value?.texto,
        )
    }

    @Test
    fun crear_vacia_el_campo_y_dice_que_se_ha_anadido() {
        mostrar()

        compose.onNodeWithTag(ETIQUETA_NUEVA_ETIQUETA).performTextInput("trabajo")
        compose.onNodeWithText(Textos.crearEtiqueta).performClick()
        compose.waitForIdle()

        assertEquals(listOf("trabajo"), creadas)
        assertEquals("Etiqueta «trabajo» añadida", anuncios.actual.value?.texto)
        compose.onNodeWithText("trabajo").assertDoesNotExist()
    }
}
