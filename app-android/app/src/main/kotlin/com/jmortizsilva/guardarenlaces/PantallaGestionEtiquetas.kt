package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.delay

/** Adónde llevar el cursor al cerrar un diálogo, y qué decir al llegar. */
private data class DestinoEnGestion(val etiqueta: String?, val anuncio: String?)

/**
 * Renombrar o eliminar una etiqueta en todos los enlaces a la vez, y crear las que se quieran tener
 * listas antes de usarlas. Lo mismo que `PantallaGestionEtiquetas` en el iPhone.
 *
 * Renombrar y eliminar devuelven lo que hay que decir, o nulo si no ha cambiado nada; la pantalla
 * lleva antes el cursor adonde toca y lo dice después, como la lista al eliminar un enlace.
 */
@Composable
fun PantallaGestionEtiquetas(
    etiquetas: List<Pair<String, Int>>,
    anuncios: Anuncios,
    alVolver: () -> Unit,
    crear: (String) -> String?,
    renombrar: (vieja: String, nueva: String) -> String?,
    eliminar: (String) -> String,
) {
    var renombrando by remember { mutableStateOf<String?>(null) }
    var eliminando by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var destino by remember { mutableStateOf<DestinoEnGestion?>(null) }
    val nueva = rememberTextFieldState()
    val estadoLista = rememberLazyListState()
    val movedor = movedorDeCursor()

    fun crearLaNueva() {
        val anuncio = crear(nueva.text.toString())
        nueva.clearText()
        anuncio?.let(anuncios::importante)
    }

    LaunchedEffect(destino) {
        val adonde = destino ?: return@LaunchedEffect
        // Lo mismo que en la lista: que el diálogo termine de cerrarse antes de mover el cursor.
        delay(ESPERA_TRAS_CERRAR_MS)
        val indice = etiquetas.indexOfFirst { it.first == adonde.etiqueta }
        if (indice >= 0) {
            estadoLista.scrollToItem(indice)
            movedor.llevarA(etiquetaDeGestion(adonde.etiqueta!!))
        } else {
            // Sin ninguna etiqueta que quede, al campo de crear, que es lo único que hay.
            movedor.llevarA(ETIQUETA_NUEVA_ETIQUETA)
        }
        // Primero el cursor y después el anuncio: mover el cursor corta lo que se esté diciendo.
        adonde.anuncio?.let {
            delay(ESPERA_TRAS_CERRAR_MS)
            anuncios.importante(it)
        }
        destino = null
    }

    CursorAlTituloAlEntrar()
    Scaffold(
        topBar = { BarraSuperior(Textos.gestionarEtiquetas, alVolver = alVolver) },
        bottomBar = { LineaDeAvisos(anuncios) },
    ) { margen ->
        LazyColumn(state = estadoLista, modifier = Modifier.padding(margen).fillMaxSize()) {
            items(etiquetas, key = { it.first }) { (nombre, enlaces) ->
                FilaEtiqueta(
                    nombre = nombre,
                    enlaces = enlaces,
                    alRenombrar = { renombrando = nombre },
                    alEliminar = { eliminando = nombre to enlaces },
                )
                HorizontalDivider()
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
                        onKeyboardAction = { crearLaNueva() },
                        modifier = Modifier.weight(1f).testTag(ETIQUETA_NUEVA_ETIQUETA),
                    )
                    Button(onClick = ::crearLaNueva, enabled = nueva.text.isNotBlank()) {
                        Text(Textos.crearEtiqueta)
                    }
                }
            }
        }
    }

    renombrando?.let { vieja ->
        val nombre = rememberTextFieldState(vieja)
        fun cerrar(anuncio: String?) {
            renombrando = null
            // Si se ha renombrado, la fila es ya la del nombre nuevo, y si ese nombre existía las
            // dos se han fundido en una: en los dos casos, la de ese nombre.
            val nuevo = nombre.text.toString().trim()
            destino = DestinoEnGestion(if (anuncio != null) nuevo else vieja, anuncio)
        }
        AlertDialog(
            onDismissRequest = { cerrar(null) },
            title = { Text(Textos.renombrar) },
            text = {
                OutlinedTextField(
                    state = nombre,
                    label = { Text(Textos.nuevoNombre) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { cerrar(renombrar(vieja, nombre.text.toString())) },
                    enabled = nombre.text.isNotBlank(),
                ) {
                    Text(Textos.guardar)
                }
            },
            dismissButton = { TextButton(onClick = { cerrar(null) }) { Text(Textos.cancelar) } },
        )
    }

    eliminando?.let { (nombre, enlaces) ->
        val indice = etiquetas.indexOfFirst { it.first == nombre }
        fun cancelar() {
            eliminando = null
            destino = DestinoEnGestion(nombre, anuncio = null)
        }
        // Sin título: la pregunta ya dice qué se va a hacer, y un título «Eliminar» encima sería
        // una parada más antes de llegar a ella.
        AlertDialog(
            onDismissRequest = ::cancelar,
            text = { Text(Textos.preguntaEliminarEtiqueta(nombre, enlaces)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // La vecina se busca antes de eliminar, que después ya no se sabe dónde
                        // estaba: la siguiente, o la anterior si era la última.
                        val vecina =
                            etiquetas.getOrNull(indice + 1) ?: etiquetas.getOrNull(indice - 1)
                        eliminando = null
                        destino = DestinoEnGestion(vecina?.first, eliminar(nombre))
                    }
                ) {
                    Text(Textos.eliminar)
                }
            },
            dismissButton = { TextButton(onClick = ::cancelar) { Text(Textos.cancelar) } },
        )
    }
}

/**
 * Como la fila de un enlace: un solo elemento para TalkBack, que dice el nombre y cuántos enlaces
 * la llevan, con Renombrar y Eliminar en sus acciones. El recuento va en la fila porque sin él hay
 * que salir a contar antes de decidir. Nada de pulsación larga ni de deslizar, por lo mismo que en
 * `FilaEnlace`.
 */
@Composable
private fun FilaEtiqueta(
    nombre: String,
    enlaces: Int,
    alRenombrar: () -> Unit,
    alEliminar: () -> Unit,
) {
    val acciones = listOf(Textos.renombrar to alRenombrar, Textos.eliminar to alEliminar)
    val texto = Textos.etiquetaConRecuento(nombre, enlaces)
    var menuAbierto by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .testTag(etiquetaDeGestion(nombre))
                .semantics(mergeDescendants = true) {
                    contentDescription = texto
                    customActions = acciones.map { (etiqueta, accion) ->
                        CustomAccessibilityAction(etiqueta) {
                            accion()
                            true
                        }
                    }
                }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(texto, modifier = Modifier.weight(1f).clearAndSetSemantics {})
        // Para quien mira la pantalla, oculto a TalkBack y a Voice Access: ver `FilaEnlace`.
        Box {
            IconButton(
                onClick = { menuAbierto = true },
                modifier = Modifier.clearAndSetSemantics {},
            ) {
                Icon(painterResource(R.drawable.icono_mas_opciones), contentDescription = null)
            }
            DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                acciones.forEach { (etiqueta, accion) ->
                    DropdownMenuItem(
                        text = { Text(etiqueta) },
                        onClick = {
                            menuAbierto = false
                            accion()
                        },
                    )
                }
            }
        }
    }
}

private const val ESPERA_TRAS_CERRAR_MS = 300L

/** Para llevar el cursor de TalkBack a la fila de una etiqueta. No se lee ni se ve. */
fun etiquetaDeGestion(nombre: String) = "gestion-$nombre"

const val ETIQUETA_NUEVA_ETIQUETA = "nueva-etiqueta"
