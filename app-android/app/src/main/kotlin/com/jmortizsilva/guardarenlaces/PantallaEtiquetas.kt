package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jmortizsilva.guardarenlaces.dominio.Biblioteca
import com.jmortizsilva.guardarenlaces.dominio.Textos

/**
 * Elegir las etiquetas de un enlace, y crear alguna que todavía no exista. «Guardar» aplica y
 * vuelve; «Cancelar» y el gesto de atrás vuelven sin tocar nada.
 */
@Composable
fun PantallaEtiquetas(
    disponibles: List<String>,
    elegidasAlEntrar: List<String>,
    anuncios: Anuncios,
    alCancelar: () -> Unit,
    alGuardar: (List<String>) -> Unit,
) {
    val eleccion = recordarEleccionDeEtiquetas(disponibles, elegidasAlEntrar)

    CursorAlTituloAlEntrar()
    Scaffold(
        topBar = {
            BarraSuperior(Textos.editarEtiquetas, alVolver = null) {
                TextButton(onClick = alCancelar) { Text(Textos.cancelar) }
                TextButton(onClick = { alGuardar(eleccion.marcadas()) }) { Text(Textos.guardar) }
            }
        },
        bottomBar = { LineaDeAvisos(anuncios) },
    ) { margen ->
        LazyColumn(Modifier.padding(margen).fillMaxSize()) { casillasDeEtiquetas(eleccion) }
    }
}

/**
 * Qué etiquetas lleva el enlace mientras se eligen. La comparten esta pantalla y la de compartir
 * desde otra aplicación, para que elegir y crear una etiqueta funcione igual en las dos.
 */
class EleccionDeEtiquetas(
    private val disponibles: List<String>,
    elegidas: MutableState<List<String>>,
    recienCreadas: MutableState<List<String>>,
    val nueva: TextFieldState,
) {
    var elegidas by elegidas
        private set

    // Las creadas aquí mismo, para que aparezcan en la lista aunque no las lleve ningún otro
    // enlace todavía.
    private var recienCreadas by recienCreadas

    val todas: List<String>
        get() = Biblioteca.etiquetasDisponibles(emptyList(), disponibles + recienCreadas + elegidas)

    fun alternar(etiqueta: String) {
        elegidas = if (etiqueta in elegidas) elegidas - etiqueta else elegidas + etiqueta
    }

    fun crear() {
        val limpia = nueva.text.toString().trim()
        if (limpia.isEmpty()) return
        if (limpia !in todas) recienCreadas = recienCreadas + limpia
        if (limpia !in elegidas) elegidas = elegidas + limpia
        nueva.clearText()
    }

    /** Las marcadas, en orden alfabético. */
    fun marcadas(): List<String> = todas.filter { it in elegidas }
}

@Composable
fun recordarEleccionDeEtiquetas(
    disponibles: List<String>,
    elegidasAlEntrar: List<String>,
): EleccionDeEtiquetas {
    val elegidas = rememberSaveable { mutableStateOf(elegidasAlEntrar) }
    val recienCreadas = rememberSaveable { mutableStateOf(emptyList<String>()) }
    val nueva = rememberTextFieldState()
    return remember(disponibles) {
        EleccionDeEtiquetas(disponibles, elegidas, recienCreadas, nueva)
    }
}

/** Una casilla por etiqueta y, al final, el campo para crear una nueva. */
fun LazyListScope.casillasDeEtiquetas(eleccion: EleccionDeEtiquetas) {
    items(eleccion.todas, key = { it }) { etiqueta ->
        val marcada = etiqueta in eleccion.elegidas
        // La fila entera como zona de toque: TalkBack dice «casilla, marcada» o «no marcada», que
        // es lo que en iOS da el rasgo de seleccionado.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .toggleable(
                        value = marcada,
                        role = Role.Checkbox,
                        onValueChange = { eleccion.alternar(etiqueta) },
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Checkbox(checked = marcada, onCheckedChange = null)
            Text(etiqueta, modifier = Modifier.padding(start = 8.dp))
        }
    }
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            OutlinedTextField(
                state = eleccion.nueva,
                label = { Text(Textos.nuevaEtiqueta) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                onKeyboardAction = { eleccion.crear() },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = eleccion::crear, enabled = eleccion.nueva.text.isNotBlank()) {
                Text(Textos.anadir)
            }
        }
    }
}
