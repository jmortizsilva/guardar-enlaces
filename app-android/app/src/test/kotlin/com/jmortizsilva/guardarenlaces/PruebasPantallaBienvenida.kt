package com.jmortizsilva.guardarenlaces

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmortizsilva.guardarenlaces.dominio.EnlacesEnElTelefono
import com.jmortizsilva.guardarenlaces.dominio.Login
import com.jmortizsilva.guardarenlaces.dominio.Proveedor
import com.jmortizsilva.guardarenlaces.dominio.Textos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PruebasPantallaBienvenida {
    @get:Rule val compose = createComposeRule()

    /** Lo que recibe `alTerminar`; vacía mientras no se ha llamado. */
    private val terminada = mutableListOf<String?>()

    private fun mostrar(entrar: Entrar = { _, _ -> Login.Resultado.Cancelado }) {
        compose.setContent {
            CompositionLocalProvider(LocalMovedorDeCursor provides MovedorDeCursor {}) {
                PantallaBienvenida(
                    anuncios = Anuncios(),
                    entrar = entrar,
                    alTerminar = { terminada += it },
                )
            }
        }
    }

    private val esBoton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `dice que es la app y que cambia con cuenta, con el titulo como encabezado`() {
        mostrar()

        compose
            .onNodeWithText(Textos.bienvenidaTitulo)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(Textos.bienvenidaQueEs).assertExists()
        compose.onNodeWithText(Textos.bienvenidaCuenta).assertExists()
    }

    @Test
    fun `las tres opciones son botones, Google antes que Apple, cada una con su explicacion`() {
        mostrar()

        compose.onNodeWithText(Textos.entrarConGoogle).assert(esBoton)
        compose.onNodeWithText(Textos.entrarConApple).assert(esBoton)
        compose.onNodeWithText(Textos.usarSinCuenta).assert(esBoton)
        val google = compose.onNodeWithText(Textos.entrarConGoogle).fetchSemanticsNode()
        val apple = compose.onNodeWithText(Textos.entrarConApple).fetchSemanticsNode()
        assertTrue(google.boundsInRoot.top < apple.boundsInRoot.top)
        // La explicación del navegador, en las dos de cuenta: en Android las dos lo abren.
        assertEquals(
            2,
            compose.onAllNodesWithText(Textos.pistaLogin).fetchSemanticsNodes().size,
        )
        compose.onNodeWithText(Textos.bienvenidaMasTarde).assertExists()
    }

    @Test
    fun `usar sin cuenta termina sin decir nada`() {
        mostrar()

        compose.onNodeWithText(Textos.usarSinCuenta).performClick()

        assertEquals(listOf<String?>(null), terminada)
    }

    @Test
    fun `al entrar bien termina diciendo que la sesion esta iniciada`() {
        var conQuien: Proveedor? = null
        mostrar(
            entrar = { proveedor, _ ->
                conQuien = proveedor
                Login.Resultado.Exito("codigo")
            }
        )

        compose.onNodeWithText(Textos.entrarConGoogle).performClick()
        compose.waitForIdle()

        assertEquals(Proveedor.Google, conQuien)
        assertEquals(listOf<String?>(Textos.sesionIniciada), terminada)
    }

    @Test
    fun `un error sale en el cuadro y se queda en la bienvenida`() {
        mostrar(entrar = { _, _ -> Login.Resultado.Error(Textos.loginFallido) })

        compose.onNodeWithText(Textos.entrarConApple).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(Textos.loginFallido).assertExists()
        compose.onNodeWithText(Textos.aceptar).performClick()
        compose.onNodeWithText(Textos.usarSinCuenta).assertExists()
        assertEquals(emptyList<String?>(), terminada)
    }

    @Test
    fun `cancelar en el navegador se queda en la bienvenida`() {
        mostrar()

        compose.onNodeWithText(Textos.entrarConGoogle).performClick()
        compose.waitForIdle()

        assertEquals(emptyList<String?>(), terminada)
    }

    @Test
    fun `si hay enlaces en el telefono pregunta antes de terminar`() {
        var importar: Boolean? = null
        mostrar(
            entrar = { _, decidir ->
                importar = decidir(EnlacesEnElTelefono(cuantos = 3, deOtraCuenta = false))
                Login.Resultado.Exito("codigo")
            }
        )

        compose.onNodeWithText(Textos.entrarConGoogle).performClick()
        compose.waitForIdle()
        compose
            .onNodeWithText(Textos.tituloEnlacesEnElTelefono)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertEquals(emptyList<String?>(), terminada)
        compose.onNodeWithText(Textos.anadirlos).performClick()
        compose.waitForIdle()

        assertEquals(true, importar)
        assertEquals(listOf<String?>(Textos.sesionIniciada), terminada)
    }
}
