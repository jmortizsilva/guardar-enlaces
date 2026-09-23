package com.jmortizsilva.guardarenlaces

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.Presentacion
import com.jmortizsilva.guardarenlaces.dominio.Textos
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La lista contra el árbol de semántica, que es de donde Compose saca lo que lee TalkBack. Lo que
 * se oye de verdad (el orden de las acciones, si suena el aviso, adónde salta el cursor) se
 * comprueba en el teléfono; aquí se fija lo que tiene que haber para que eso pueda salir bien.
 */
@RunWith(RobolectricTestRunner::class)
class PruebasPantallaLista {
    @get:Rule val compose = createComposeRule()

    private val enlaces =
        listOf(
            Elemento(
                id = "a",
                url = "https://www.ejemplo.com/uno",
                titulo = "Primero",
                etiquetas = listOf("ocio"),
                creadoEn = 1_710_504_000_000,
            ),
            Elemento(id = "b", url = "https://ejemplo.com/dos", titulo = "Segundo", creadoEn = 2),
            Elemento(id = "c", url = "https://ejemplo.com/tres", titulo = "Tercero", creadoEn = 1),
        )

    private val anuncios = Anuncios()
    private val abiertos = mutableListOf<String>()
    private val copiados = mutableListOf<String>()

    private fun mostrar(conCuenta: Boolean = false): MutableList<Elemento> {
        val guardados = mutableStateListOf(*enlaces.toTypedArray())
        compose.setContent {
            PantallaLista(
                elementos = guardados.toList(),
                etiquetasDisponibles = listOf("ocio"),
                conCuenta = conCuenta,
                anuncios = anuncios,
                alAbrir = { abiertos += it.id },
                alCopiar = { copiados += it.id },
                alEliminar = { guardados.remove(it) },
            )
        }
        return guardados
    }

    private fun descripcion(elemento: Elemento) =
        "${Presentacion.titulo(elemento)}. ${Presentacion.subtitulo(elemento)}"

    private fun fila(elemento: Elemento) =
        compose.onNodeWithContentDescription(descripcion(elemento))

    @Test
    fun el_titulo_de_la_pantalla_es_un_encabezado() {
        mostrar()
        compose
            .onNodeWithText(Textos.tituloApp)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun cada_fila_se_lee_entera_en_una_frase() {
        mostrar()

        fila(enlaces[0]).assert(hasContentDescription(descripcion(enlaces[0])))
    }

    @Test
    fun tocar_la_fila_abre_y_dice_que_abre() {
        mostrar()

        fila(enlaces[0])
            .assert(
                SemanticsMatcher("dice «${Textos.abrir}» al tocar") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == Textos.abrir
                }
            )
            .performClick()

        assertEquals(listOf("a"), abiertos)
    }

    @Test
    fun las_acciones_de_la_fila_estan_en_el_orden_en_que_se_quieren_oir() {
        mostrar()

        val acciones =
            fila(enlaces[0]).fetchSemanticsNode().config[SemanticsActions.CustomActions].map {
                it.label
            }

        assertEquals(listOf(Textos.copiarUrl, Textos.eliminar), acciones)
    }

    @Test
    fun el_boton_de_mas_opciones_no_existe_para_talkback() {
        // Las mismas acciones ya están en la fila. Si se viera, cada fila tendría un botón más que
        // recorrer.
        mostrar()

        compose
            .onAllNodesWithContentDescription(Textos.masOpciones, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .let { assertEquals(0, it.size) }
    }

    @Test
    fun copiar_desde_las_acciones_copia_esa_fila() {
        mostrar()

        fila(enlaces[1]).performCustomAction(Textos.copiarUrl)

        assertEquals(listOf("b"), copiados)
    }

    @Test
    fun eliminar_pregunta_antes_y_dice_la_consecuencia() {
        mostrar(conCuenta = true)

        fila(enlaces[0]).performCustomAction(Textos.eliminar)

        compose.onNodeWithText(Textos.preguntaEliminar("Primero")).assertExists()
        compose.onNodeWithText(Textos.consecuenciaEliminar(conCuenta = true)).assertExists()
    }

    @Test
    fun al_eliminar_el_foco_pasa_a_la_fila_siguiente_y_se_anuncia() {
        val guardados = mostrar()

        fila(enlaces[0]).performCustomAction(Textos.eliminar)
        compose.onNode(hasText(Textos.eliminar) and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals(listOf("b", "c"), guardados.map { it.id })
        fila(enlaces[1]).assertIsFocused()
        assertEquals("Eliminado, Primero", anuncios.actual.value?.texto)
    }

    @Test
    fun al_eliminar_la_ultima_fila_el_foco_va_a_la_anterior() {
        mostrar()

        fila(enlaces[2]).performCustomAction(Textos.eliminar)
        compose.onNode(hasText(Textos.eliminar) and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        fila(enlaces[1]).assertIsFocused()
    }

    @Test
    fun cancelar_devuelve_el_foco_a_la_misma_fila_y_no_dice_nada() {
        val guardados = mostrar()

        fila(enlaces[1]).performCustomAction(Textos.eliminar)
        compose.onNodeWithText(Textos.cancelar).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals(3, guardados.size)
        fila(enlaces[1]).assertIsFocused()
        assertEquals(null, anuncios.actual.value)
    }

    @Test
    fun buscar_filtra_y_la_lista_vacia_dice_por_que() {
        mostrar()

        compose.onNodeWithText(Textos.buscar).performTextInput("nada")

        compose.onNodeWithText(Textos.listaVacia("nada", null)).assertExists()
    }

    @Test
    fun el_filtro_dice_por_donde_filtra_y_filtra() {
        mostrar()

        compose.onNodeWithText(Textos.filtroPorEtiqueta(null)).performClick()
        compose.onNodeWithText("ocio").performClick()

        compose.onNodeWithText(Textos.filtroPorEtiqueta("ocio")).assertExists()
        fila(enlaces[0]).assertExists()
        fila(enlaces[1]).assertDoesNotExist()
    }

    @Test
    fun sin_enlaces_lo_dice() {
        compose.setContent { PantallaLista(emptyList(), emptyList(), false, anuncios, {}, {}, {}) }

        compose.onNodeWithText(Textos.listaVacia("", null)).assertExists()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performCustomAction(
    etiqueta: String
) {
    val accion =
        fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == etiqueta }
    accion.action()
}

private fun hasClickAction() = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
