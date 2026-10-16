package com.jmortizsilva.guardarenlaces

import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.Textos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PruebasPantallaCompartir {
    @get:Rule val compose = createComposeRule()

    private var guardadas: List<String>? = null
    private var cancelada = false

    private fun mostrar(
        metadatos: MetadatosExtraidos? = null,
        disponibles: List<String> = listOf("trabajo", "ocio"),
        guardando: Boolean = false,
    ) {
        compose.setContent {
            PantallaCompartir(
                url = "https://www.ejemplo.com/articulo/muy/largo?origen=compartir",
                metadatos = metadatos,
                comprobando = metadatos == null,
                disponibles = disponibles,
                guardando = guardando,
                anuncios = Anuncios(),
                alCancelar = { cancelada = true },
                alGuardar = { guardadas = it },
            )
        }
    }

    @Test
    fun mientras_no_hay_titulo_lo_dice_y_lee_el_sitio_no_la_direccion_entera() {
        mostrar()

        compose
            .onNodeWithContentDescription("${Textos.compartirSinTitulo}. www.ejemplo.com")
            .assertExists()
        compose.onNodeWithText(Textos.comprobando).assertExists()
    }

    @Test
    fun con_titulo_se_lee_el_titulo_y_el_sitio() {
        mostrar(metadatos = MetadatosExtraidos(titulo = "Un artículo"))

        compose.onNodeWithContentDescription("Un artículo. www.ejemplo.com").assertExists()
        compose.onNodeWithText(Textos.comprobando).assertDoesNotExist()
    }

    @Test
    fun guardar_devuelve_las_etiquetas_marcadas_y_las_creadas_aqui() {
        mostrar()

        compose.onNodeWithText("trabajo").performClick()
        compose.onNodeWithText(Textos.nuevaEtiqueta).performTextInput("recetas")
        compose.onNodeWithText(Textos.anadir).performClick()
        compose.onNodeWithText("recetas").assertIsOn()
        compose.onNodeWithText(Textos.guardar).performClick()

        assertEquals(listOf("recetas", "trabajo"), guardadas)
    }

    @Test
    fun sin_etiquetas_lo_dice_y_deja_crear_una() {
        mostrar(disponibles = emptyList())

        compose.onNodeWithText(Textos.compartirSinEtiquetas).assertExists()
        compose.onNodeWithText(Textos.nuevaEtiqueta).assertExists()
    }

    @Test
    fun mientras_guarda_no_se_puede_pulsar_guardar_otra_vez() {
        mostrar(guardando = true)

        compose.onNodeWithText(Textos.guardar).assertIsNotEnabled()
    }

    @Test
    fun cancelar_no_guarda_nada() {
        mostrar()

        compose.onNodeWithText("ocio").performClick()
        compose.onNodeWithText(Textos.cancelar).performClick()

        assertEquals(true, cancelada)
        assertNull(guardadas)
    }

    @Test
    fun la_direccion_sale_del_texto_compartido_aunque_venga_dentro_de_una_frase() {
        assertEquals(
            "https://ejemplo.com/a",
            direccionCompartida(compartido("https://ejemplo.com/a")),
        )
        assertEquals(
            "https://ejemplo.com/a",
            direccionCompartida(compartido("Mira esto: https://ejemplo.com/a vía @alguien")),
        )
    }

    @Test
    fun un_texto_sin_direccion_o_algo_que_no_es_compartir_no_da_direccion() {
        assertNull(direccionCompartida(compartido("Solo una frase, sin enlace")))
        assertNull(direccionCompartida(Intent(Intent.ACTION_SEND)))
        assertNull(
            direccionCompartida(
                Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, "https://ejemplo.com")
            )
        )
    }

    private fun compartido(texto: String) =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
}
