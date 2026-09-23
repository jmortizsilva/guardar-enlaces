package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lo que se dice en voz alta, con las dos prioridades de iOS.
 *
 * `announceForAccessibility` está obsoleta desde Android 16; lo que Android recomienda para avisar
 * de un cambio es una región viva, y eso es esto: una línea de texto al pie de la pantalla que
 * TalkBack lee cuando cambia. `importante` corta lo que se esté leyendo (`Assertive`);
 * `informativo` espera su turno (`Polite`). Ver la tabla de accesibilidad de PLAN.md: esto hay que
 * medirlo en el teléfono, y si no alcanza, el recurso es la API obsoleta.
 */
class Anuncios {
    enum class Prioridad {
        Importante,
        Informativo,
    }

    /** `numero` distingue dos avisos con el mismo texto, que si no no volverían a sonar. */
    data class Aviso(val texto: String, val prioridad: Prioridad, val numero: Long)

    private val _actual = MutableStateFlow<Aviso?>(null)
    val actual: StateFlow<Aviso?> = _actual.asStateFlow()
    private var contador = 0L

    /** Lo que hay que oír sí o sí: un resultado o un error. */
    fun importante(texto: String) = avisar(texto, Prioridad.Importante)

    /** Para avisos que se pueden perder sin consecuencias. */
    fun informativo(texto: String) = avisar(texto, Prioridad.Informativo)

    private fun avisar(texto: String, prioridad: Prioridad) {
        _actual.value = Aviso(texto, prioridad, ++contador)
    }
}

/**
 * La región viva. Dos trampas que se esquivan aquí:
 * - Una región viva solo habla cuando su texto **cambia**. «URL copiada» dos veces seguidas sonaría
 *   solo la primera, así que antes de cada aviso se vacía un instante.
 * - Un aviso que se queda en pantalla lo vuelve a leer TalkBack al recorrerla, y ya no es verdad.
 *   Se borra a los pocos segundos.
 */
@Composable
fun LineaDeAvisos(anuncios: Anuncios, modifier: Modifier = Modifier) {
    val aviso by anuncios.actual.collectAsState()
    var mostrado by remember { mutableStateOf("") }
    // Cada pantalla tiene su línea, y al aparecer se encontraba el último aviso, fuera de cuando
    // fuera, y lo volvía a decir: al volver al detalle se oían las etiquetas de antes (medido el
    // 2026-09-23). Lo que ya estaba al aparecer no se dice.
    val yaEstaba = remember { anuncios.actual.value?.numero }

    LaunchedEffect(aviso?.numero) {
        val actual = aviso?.takeIf { it.numero != yaEstaba } ?: return@LaunchedEffect
        mostrado = ""
        delay(PAUSA_ENTRE_AVISOS_MS)
        mostrado = actual.texto
        delay(DURACION_EN_PANTALLA_MS)
        mostrado = ""
    }

    val modo =
        if (aviso?.prioridad == Anuncios.Prioridad.Informativo) LiveRegionMode.Polite
        else LiveRegionMode.Assertive
    Text(
        text = mostrado,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics {
                liveRegion = modo
            },
    )
}

private const val PAUSA_ENTRE_AVISOS_MS = 100L
private const val DURACION_EN_PANTALLA_MS = 8_000L
