package com.jmortizsilva.guardarenlaces

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmortizsilva.guardarenlaces.dominio.Textos
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PruebasPantallaAjustes {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `importar es un boton que dice que se puede traer`() {
        compose.setContent { PantallaAjustes(alVolver = {}, importar = { "" }) }

        // El nombre y la explicación en la misma parada: en Android no hay pista aparte, y un
        // segundo elemento suelto sería una parada más que no dice qué hace.
        compose
            .onNodeWithText(Textos.importarEnlaces, useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithText(Textos.pistaImportar, substring = true).assertExists()
    }

    @Test
    fun `el resultado se lee en el cuadro y aceptar lo cierra`() {
        var aceptado = false
        val texto = Textos.resultadoImportacion(143, 12)
        compose.setContent { CuadroResultado(texto, alAceptar = { aceptado = true }) }

        compose.onNodeWithText(texto).assertExists()
        compose.onNodeWithText(Textos.aceptar).performClick()

        assertTrue(aceptado)
    }
}
