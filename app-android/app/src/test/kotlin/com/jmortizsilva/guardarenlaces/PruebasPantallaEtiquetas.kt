package com.jmortizsilva.guardarenlaces

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jmortizsilva.guardarenlaces.dominio.Textos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PruebasPantallaEtiquetas {
    @get:Rule val compose = createComposeRule()

    private var guardadas: List<String>? = null
    private var cancelada = false

    private fun mostrar() {
        compose.setContent {
            PantallaEtiquetas(
                disponibles = listOf("trabajo", "ocio", "casa"),
                elegidasAlEntrar = listOf("ocio"),
                anuncios = Anuncios(),
                alCancelar = { cancelada = true },
                alGuardar = { guardadas = it },
            )
        }
    }

    @Test
    fun cada_etiqueta_es_una_casilla_marcada_o_no() {
        mostrar()

        compose.onNodeWithText("ocio").assertIsOn()
        compose.onNodeWithText("casa").assertIsOff()
    }

    @Test
    fun guardar_devuelve_las_marcadas_en_orden_alfabetico() {
        mostrar()

        compose.onNodeWithText("trabajo").performClick()
        compose.onNodeWithText("casa").performClick()
        compose.onNodeWithText("ocio").performClick()
        compose.onNodeWithText(Textos.guardar).performClick()

        assertEquals(listOf("casa", "trabajo"), guardadas)
    }

    @Test
    fun una_etiqueta_nueva_aparece_marcada() {
        mostrar()

        compose.onNodeWithText(Textos.nuevaEtiqueta).performTextInput("  recetas  ")
        compose.onNodeWithText(Textos.anadir).performClick()

        compose.onNodeWithText("recetas").assertIsOn()
        compose.onNodeWithText(Textos.guardar).performClick()
        assertEquals(listOf("ocio", "recetas"), guardadas)
    }

    @Test
    fun cancelar_no_guarda_nada() {
        mostrar()

        compose.onNodeWithText("casa").performClick()
        compose.onNodeWithText(Textos.cancelar).performClick()

        assertEquals(true, cancelada)
        assertNull(guardadas)
    }
}
