package com.jmortizsilva.guardarenlaces

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PruebasPantallaAnadir {
    @get:Rule val compose = createComposeRule()

    private val anuncios = Anuncios()
    private val cursor = mutableListOf<String>()
    private var guardado: Triple<String, List<String>, MetadatosExtraidos?>? = null
    private val yaGuardado = Elemento(id = "e1", url = "https://www.ejemplo.com/ya")

    private fun mostrar(
        copiado: Copiado = Copiado.Nada,
        portapapeles: String? = null,
        comprobar: suspend (String) -> MetadatosExtraidos? = { null },
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalMovedorDeCursor provides MovedorDeCursor { cursor += it }
            ) {
                PantallaAnadir(
                    borrador = BorradorEnlace(),
                    anuncios = anuncios,
                    copiado = copiado,
                    llegada = null,
                    alAtenderLlegada = {},
                    repetido = { url -> yaGuardado.takeIf { url.endsWith("/ya") } },
                    comprobar = comprobar,
                    leerPortapapeles = { portapapeles },
                    alElegirEtiquetas = {},
                    alCancelar = {},
                    alGuardar = { url, etiquetas, metadatos ->
                        guardado = Triple(url, etiquetas, metadatos)
                    },
                )
            }
        }
    }

    private fun esperar(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test
    fun al_abrir_el_cursor_va_al_campo_de_la_url() {
        mostrar()
        esperar(2_000)

        assertEquals(listOf(ETIQUETA_CAMPO_URL), cursor)
    }

    @Test
    fun sin_una_direccion_valida_no_se_puede_guardar_y_se_dice_por_que() {
        mostrar()

        compose.onNodeWithText(Textos.guardar).assertIsNotEnabled()
        compose.onNodeWithText(Textos.campoUrl).performTextInput("nota para el lunes")

        compose.onNodeWithText(Textos.urlNoValida).assertExists()
        compose.onNodeWithText(Textos.guardar).assertIsNotEnabled()
    }

    @Test
    fun con_la_comprobacion_hecha_se_guarda_con_su_titulo() {
        mostrar(comprobar = { MetadatosExtraidos(titulo = "Un artículo") })

        compose.onNodeWithText(Textos.campoUrl).performTextInput("https://ejemplo.com/a")
        esperar(1_000)
        // Como la oye TalkBack: título y descripción de una vez, aquí sin descripción.
        compose.onNodeWithContentDescription("Un artículo").assertExists()
        compose.onNodeWithText(Textos.guardar).assertIsEnabled().performClick()
        esperar(100)

        assertEquals("https://ejemplo.com/a", guardado?.first)
        assertEquals("Un artículo", guardado?.third?.titulo)
    }

    @Test
    fun si_la_pagina_no_contesta_guarda_igual_a_los_dos_segundos_sin_titulo() {
        mostrar(comprobar = { awaitCancellation() })

        compose.onNodeWithText(Textos.campoUrl).performTextInput("https://lenta.com")
        esperar(600)
        compose.onNodeWithText(Textos.comprobando).assertExists()
        compose.onNodeWithText(Textos.guardar).performClick()
        esperar(1_000)
        assertNull(guardado)
        esperar(1_500)

        assertEquals("https://lenta.com", guardado?.first)
        assertNull(guardado?.third)
    }

    @Test
    fun un_enlace_repetido_se_avisa_al_momento_y_el_boton_dice_actualizar() {
        mostrar()

        compose.onNodeWithText(Textos.campoUrl).performTextInput("https://ejemplo.com/ya")
        esperar(100)

        compose.onNodeWithText(Textos.actualizar).assertExists()
        compose.onNodeWithText(Textos.enlaceRepetido).assertExists()
        assertEquals(Textos.enlaceRepetido, anuncios.actual.value?.texto)
    }

    @Test
    fun con_un_enlace_copiado_aparece_pegar_y_se_dice() {
        mostrar(copiado = Copiado.Enlace, portapapeles = "Mira: https://ejemplo.com/p vía @x")
        esperar(1_000)

        assertEquals(Textos.hayEnlaceCopiado, anuncios.actual.value?.texto)
        compose.onNodeWithText(Textos.pegar).performClick()

        compose.onNodeWithText("https://ejemplo.com/p").assertExists()
        // Con algo escrito ya no hay nada que pegar.
        compose.onNodeWithText(Textos.pegar).assertDoesNotExist()
    }

    @Test
    fun con_texto_que_puede_no_ser_un_enlace_se_dice_asi() {
        mostrar(copiado = Copiado.Texto)
        esperar(1_000)

        assertEquals(Textos.hayTextoCopiado, anuncios.actual.value?.texto)
    }

    @Test
    fun sin_nada_copiado_no_hay_boton_ni_aviso() {
        mostrar()
        esperar(1_000)

        compose.onNodeWithText(Textos.pegar).assertDoesNotExist()
        assertNull(anuncios.actual.value)
    }
}
