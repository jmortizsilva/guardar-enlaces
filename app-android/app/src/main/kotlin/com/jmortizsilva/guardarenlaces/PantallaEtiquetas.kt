package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    var elegidas by rememberSaveable { mutableStateOf(elegidasAlEntrar) }
    // Las creadas aquí mismo, para que aparezcan en la lista aunque no las lleve ningún otro
    // enlace todavía.
    var recienCreadas by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val nueva = rememberTextFieldState()
    val todas =
        remember(disponibles, recienCreadas, elegidas) {
            Biblioteca.etiquetasDisponibles(emptyList(), disponibles + recienCreadas + elegidas)
        }

    fun crear() {
        val limpia = nueva.text.toString().trim()
        if (limpia.isEmpty()) return
        if (limpia !in todas) recienCreadas = recienCreadas + limpia
        if (limpia !in elegidas) elegidas = elegidas + limpia
        nueva.clearText()
    }

    CursorAlTituloAlEntrar()
    Scaffold(
        topBar = {
            BarraSuperior(Textos.editarEtiquetas, alVolver = null) {
                TextButton(onClick = alCancelar) { Text(Textos.cancelar) }
                TextButton(onClick = { alGuardar(todas.filter { it in elegidas }) }) {
                    Text(Textos.guardar)
                }
            }
        },
        bottomBar = { LineaDeAvisos(anuncios) },
    ) { margen ->
        LazyColumn(Modifier.padding(margen).fillMaxSize()) {
            items(todas, key = { it }) { etiqueta ->
                val marcada = etiqueta in elegidas
                // Una casilla por etiqueta, con la fila entera como zona de toque: TalkBack dice
                // «casilla, marcada» o «no marcada», que es lo que en iOS da el rasgo de
                // seleccionado.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.fillMaxWidth()
                            .toggleable(
                                value = marcada,
                                role = Role.Checkbox,
                                onValueChange = {
                                    elegidas =
                                        if (marcada) elegidas - etiqueta else elegidas + etiqueta
                                },
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
                        state = nueva,
                        label = { Text(Textos.nuevaEtiqueta) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                        onKeyboardAction = { crear() },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = ::crear, enabled = nueva.text.isNotBlank()) {
                        Text(Textos.anadir)
                    }
                }
            }
        }
    }
}
