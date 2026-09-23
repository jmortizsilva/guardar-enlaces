package com.jmortizsilva.guardarenlaces

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jmortizsilva.guardarenlaces.dominio.Textos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Comprueba que las pruebas de interfaz corren en la JVM con Robolectric y leen el árbol de
 * semántica, que es de donde Compose saca lo que lee TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
class PruebasPantallaInicial {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `el titulo es un encabezado`() {
        compose.setContent { PantallaInicial() }
        compose
            .onNodeWithText(Textos.tituloApp)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
