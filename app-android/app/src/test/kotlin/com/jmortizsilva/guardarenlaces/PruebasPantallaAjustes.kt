package com.jmortizsilva.guardarenlaces

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmortizsilva.guardarenlaces.ModeloApp.EstadoCuenta
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
class PruebasPantallaAjustes {
    @get:Rule val compose = createComposeRule()

    private val cursor = mutableListOf<String>()

    private fun mostrar(
        cuenta: EstadoCuenta = EstadoCuenta.SinCuenta,
        entrar: suspend (Proveedor, suspend (EnlacesEnElTelefono) -> Boolean) -> Login.Resultado =
            { _, _ ->
                Login.Resultado.Cancelado
            },
        cerrarSesion: suspend () -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalMovedorDeCursor provides MovedorDeCursor { cursor += it }
            ) {
                PantallaAjustes(
                    cuenta = cuenta,
                    anuncios = Anuncios(),
                    alVolver = {},
                    importar = { "" },
                    entrar = entrar,
                    cerrarSesion = cerrarSesion,
                )
            }
        }
    }

    private val esBoton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `importar es un boton que dice que se puede traer`() {
        mostrar()

        // El nombre y la explicación en la misma parada: en Android no hay pista aparte, y un
        // segundo elemento suelto sería una parada más que no dice qué hace.
        compose.onNodeWithText(Textos.importarEnlaces).assert(esBoton)
        compose.onNodeWithText(Textos.pistaImportar, substring = true).assertExists()
    }

    @Test
    fun `sin cuenta lo dice y ofrece las dos, avisando del navegador`() {
        mostrar()

        compose.onNodeWithText(Textos.sinCuenta).assertExists()
        compose.onNodeWithText(Textos.entrarConGoogle).assert(esBoton)
        compose.onNodeWithText(Textos.entrarConApple).assert(esBoton)
    }

    @Test
    fun `con cuenta dice de quien es y ofrece cerrar sesion`() {
        mostrar(cuenta = EstadoCuenta.ConCuenta("persona@ejemplo.com"))

        compose.onNodeWithText(Textos.sesionIniciadaComo("persona@ejemplo.com")).assertExists()
        compose.onNodeWithText(Textos.cerrarSesion).assert(esBoton)
        compose.onNodeWithText(Textos.entrarConGoogle).assertDoesNotExist()
    }

    @Test
    fun `con cuenta pero sin saber todavia de quien, no se inventa un correo`() {
        mostrar(cuenta = EstadoCuenta.ConCuenta(email = null))

        compose.onNodeWithText(Textos.sesionIniciadaSinCorreo).assertExists()
    }

    @Test
    fun `al entrar, el cursor va al texto que dice con que cuenta`() {
        var conQuien: Proveedor? = null
        mostrar(
            entrar = { proveedor, _ ->
                conQuien = proveedor
                Login.Resultado.Exito("codigo")
            }
        )

        compose.onNodeWithText(Textos.entrarConApple).performClick()
        compose.waitForIdle()

        assertEquals(Proveedor.Apple, conQuien)
        assertEquals(listOf(ETIQUETA_CUENTA), cursor.filter { it == ETIQUETA_CUENTA })
    }

    @Test
    fun `un error al entrar sale en el cuadro que se puede releer`() {
        mostrar(entrar = { _, _ -> Login.Resultado.Error(Textos.loginFallido) })

        compose.onNodeWithText(Textos.entrarConGoogle).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(Textos.loginFallido).assertExists()
        compose.onNodeWithText(Textos.aceptar).assertExists()
    }

    @Test
    fun `si hay enlaces en el telefono pregunta, y anadirlos responde que si`() {
        var importar: Boolean? = null
        mostrar(
            entrar = { _, decidir ->
                importar = decidir(EnlacesEnElTelefono(cuantos = 3, deOtraCuenta = false))
                Login.Resultado.Exito("codigo")
            }
        )

        compose.onNodeWithText(Textos.entrarConGoogle).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(Textos.preguntaImportar(3, deOtraCuenta = false)).assertExists()
        compose
            .onNodeWithText(Textos.tituloEnlacesEnElTelefono)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(Textos.anadirlos).performClick()
        compose.waitForIdle()

        assertTrue(importar == true)
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
