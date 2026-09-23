package com.jmortizsilva.guardarenlaces

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Adónde se pide llevar el cursor de TalkBack en cada caso. Que llegue de verdad solo se sabe en el
 * teléfono; aquí se fija que se pida al sitio correcto y una sola vez, que es donde estaban los
 * fallos medidos el 2026-09-23.
 */
@RunWith(RobolectricTestRunner::class)
class PruebasCursorYAvisos {
    @get:Rule val compose = createComposeRule()

    private val pedidos = mutableListOf<String>()
    private val grabador = MovedorDeCursor { pedidos += it }

    private val elemento =
        Elemento(id = "e1", url = "https://a.com", titulo = "Uno", etiquetas = listOf("ocio"))

    private fun avanzar() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun al_abrir_el_detalle_el_cursor_va_al_titulo() {
        compose.setContent {
            CompositionLocalProvider(LocalMovedorDeCursor provides grabador) {
                PantallaDetalle(elemento, false, Anuncios(), null, {}, {}, {}, {}, {}, {})
            }
        }
        avanzar()

        assertEquals(listOf(ETIQUETA_TITULO), pedidos)
    }

    @Test
    fun al_volver_al_detalle_desde_las_etiquetas_va_al_boton_y_no_salta_luego_al_titulo() {
        val anuncios = Anuncios()
        compose.setContent {
            var llegada by remember {
                mutableStateOf<Llegada?>(Llegada.ABotonEtiquetas("Etiquetas: casa"))
            }
            CompositionLocalProvider(LocalMovedorDeCursor provides grabador) {
                PantallaDetalle(
                    elemento,
                    false,
                    anuncios,
                    llegada,
                    alAtenderLlegada = { llegada = null },
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        avanzar()

        assertEquals(listOf(ETIQUETA_BOTON_ETIQUETAS), pedidos)
        assertEquals("Etiquetas: casa", anuncios.actual.value?.texto)
    }

    @Test
    fun al_eliminar_desde_el_detalle_la_lista_lleva_el_cursor_a_la_fila_vecina() {
        val guardados =
            androidx.compose.runtime.mutableStateListOf(
                elemento,
                Elemento(id = "e2", url = "https://b.com", titulo = "Dos"),
            )
        compose.setContent {
            CompositionLocalProvider(LocalMovedorDeCursor provides grabador) {
                PantallaLista(
                    elementos = guardados.toList(),
                    etiquetasDisponibles = emptyList(),
                    conCuenta = false,
                    anuncios = Anuncios(),
                    alAbrir = {},
                    alCopiar = {},
                    alEliminar = { guardados.remove(it) },
                    llegada = Llegada.EliminarFila("e1"),
                )
            }
        }
        avanzar()

        assertEquals(listOf(etiquetaDeFila("e2")), pedidos)
    }

    @Test
    fun una_pantalla_nueva_no_repite_el_ultimo_aviso() {
        val anuncios = Anuncios().apply { importante("Etiquetas: lo de antes") }
        compose.setContent {
            CompositionLocalProvider(LocalMovedorDeCursor provides grabador) {
                LineaDeAvisos(anuncios)
            }
        }
        avanzar()

        compose.onNodeWithText("Etiquetas: lo de antes").assertDoesNotExist()
    }

    @Test
    fun un_aviso_nuevo_si_se_dice() {
        val anuncios = Anuncios().apply { importante("lo de antes") }
        compose.setContent { LineaDeAvisos(anuncios) }
        compose.waitForIdle()

        anuncios.importante("Eliminado, Uno")
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        compose.onNodeWithText("Eliminado, Uno").assertExists()
    }
}
