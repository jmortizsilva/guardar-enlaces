package com.jmortizsilva.guardarenlaces

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.Textos
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PruebasPantallaDetalle {
    @get:Rule val compose = createComposeRule()

    private val elemento =
        Elemento(
            id = "e1",
            url = "https://www.xataka.com/basics/alternativas-pocket",
            titulo = "Las mejores alternativas a Pocket",
            descripcion = "Pocket cierra y estas son las opciones",
            etiquetas = listOf("ocio", "pendiente"),
            creadoEn = 1_710_504_000_000,
        )
    private val pedidos = mutableListOf<String>()

    private fun mostrar(conCuenta: Boolean = false) {
        compose.setContent {
            PantallaDetalle(
                elemento = elemento,
                conCuenta = conCuenta,
                anuncios = Anuncios(),
                llegada = null,
                alAtenderLlegada = {},
                alVolver = { pedidos += "volver" },
                alEditarEtiquetas = { pedidos += "etiquetas" },
                alAbrir = { pedidos += "abrir" },
                alCopiar = { pedidos += "copiar" },
                alEliminar = { pedidos += "eliminar" },
            )
        }
    }

    @Test
    fun el_titulo_del_enlace_es_el_encabezado() {
        mostrar()
        compose
            .onNodeWithText("Las mejores alternativas a Pocket")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun cada_dato_se_lee_con_su_nombre_delante() {
        mostrar()

        compose
            .onNodeWithContentDescription("URL: https://www.xataka.com/basics/alternativas-pocket")
            .assertExists()
        compose
            .onNodeWithContentDescription("Descripción: Pocket cierra y estas son las opciones")
            .assertExists()
        compose.onNodeWithText("Guardado el", substring = true).assertExists()
    }

    @Test
    fun las_etiquetas_dicen_cuales_son_y_que_tocar_las_edita() {
        mostrar()

        compose
            .onNodeWithText("Etiquetas: ocio, pendiente")
            .assert(
                SemanticsMatcher("dice «${Textos.editarEtiquetas}» al tocar") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == Textos.editarEtiquetas
                }
            )
            .performClick()

        assertEquals(listOf("etiquetas"), pedidos)
    }

    @Test
    fun sin_etiquetas_lo_dice() {
        compose.setContent {
            PantallaDetalle(
                elemento.conEtiquetas(emptyList()),
                false,
                Anuncios(),
                null,
                {},
                {},
                {},
                {},
                {},
                {},
            )
        }

        compose.onNodeWithText("Etiquetas: Ninguna").assertExists()
    }

    @Test
    fun volver_esta_en_la_barra() {
        mostrar()

        compose.onNodeWithContentDescription(Textos.volver).performClick()

        assertEquals(listOf("volver"), pedidos)
    }

    @Test
    fun eliminar_pregunta_antes_y_solo_al_aceptar_elimina() {
        mostrar(conCuenta = true)

        compose.onNodeWithText(Textos.eliminar).performScrollTo().performClick()
        compose.onNodeWithText(Textos.consecuenciaEliminar(conCuenta = true)).assertExists()
        assertEquals(emptyList<String>(), pedidos)

        // Dos «Eliminar» en pantalla: el del detalle y el del diálogo, que es el último.
        compose.onAllNodesWithText(Textos.eliminar).fetchSemanticsNodes().size.let {
            assertEquals(2, it)
        }
        compose.onAllNodesWithText(Textos.eliminar)[1].performClick()

        assertEquals(listOf("eliminar"), pedidos)
    }
}
