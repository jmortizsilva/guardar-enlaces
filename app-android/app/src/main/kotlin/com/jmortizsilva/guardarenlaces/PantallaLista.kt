package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jmortizsilva.guardarenlaces.dominio.Biblioteca
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.Presentacion
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.delay

/**
 * Adónde llevar el foco después de un diálogo o de quitar una fila, y qué decir al llegar. `id`
 * nulo es el mensaje de lista vacía.
 */
private data class DestinoDelFoco(val id: String?, val anuncio: String?)

/**
 * La lista de enlaces, con búsqueda y filtro.
 *
 * No sabe de dónde salen los enlaces ni qué pasa al eliminar: lo recibe todo de fuera. Así las
 * pruebas la recorren con una lista escrita a mano, sin base de datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLista(
    elementos: List<Elemento>,
    etiquetasDisponibles: List<String>,
    conCuenta: Boolean,
    anuncios: Anuncios,
    alAbrir: (Elemento) -> Unit,
    alCopiar: (Elemento) -> Unit,
    alEliminar: (Elemento) -> Unit,
    alVerDetalles: (Elemento) -> Unit = {},
    alEditarEtiquetas: (Elemento) -> Unit = {},
    llegada: Llegada? = null,
    alAtenderLlegada: () -> Unit = {},
) {
    val busqueda = rememberTextFieldState()
    var etiqueta by rememberSaveable { mutableStateOf<String?>(null) }
    var aEliminar by remember { mutableStateOf<Elemento?>(null) }
    var destino by remember { mutableStateOf<DestinoDelFoco?>(null) }
    val focos = remember { mutableMapOf<String, FocusRequester>() }
    val focoListaVacia = remember { FocusRequester() }
    val estadoLista = rememberLazyListState()
    val movedor = movedorDeCursor()

    val visibles =
        Biblioteca.buscar(
            Biblioteca.filtrarPorEtiqueta(elementos, etiqueta),
            busqueda.text.toString(),
        )

    /**
     * Elimina y deja el cursor en la fila siguiente, o en la anterior si era la última: quedarse
     * sin sitio después de eliminar obliga a buscar desde arriba dónde se estaba. Lo usan el
     * diálogo de aquí y el del detalle.
     */
    fun eliminarConCursor(elemento: Elemento) {
        val indice = visibles.indexOfFirst { it.id == elemento.id }
        val vecina = visibles.getOrNull(indice + 1) ?: visibles.getOrNull(indice - 1)
        alEliminar(elemento)
        destino = DestinoDelFoco(vecina?.id, Textos.eliminado(Presentacion.titulo(elemento)))
    }

    LaunchedEffect(llegada) {
        when (val adonde = llegada) {
            is Llegada.AFila -> destino = DestinoDelFoco(adonde.id, adonde.anuncio)
            is Llegada.EliminarFila ->
                visibles.firstOrNull { it.id == adonde.id }?.let(::eliminarConCursor)
            else -> return@LaunchedEffect
        }
        alAtenderLlegada()
    }

    LaunchedEffect(destino) {
        val adonde = destino ?: return@LaunchedEffect
        // Un respiro para que el diálogo termine de cerrarse y la lista se recomponga. Pedido
        // antes, el foco va a un sitio que todavía no existe (la app de Expo usaba 300 ms por lo
        // mismo). A medir en el teléfono.
        delay(ESPERA_TRAS_CERRAR_MS)
        val indice = visibles.indexOfFirst { it.id == adonde.id }
        try {
            // El foco del teclado, para quien use uno.
            if (indice >= 0) {
                estadoLista.scrollToItem(indice)
                focos[adonde.id]?.requestFocus()
            } else {
                focoListaVacia.requestFocus()
            }
        } catch (_: IllegalStateException) {
            // La fila ya no está en pantalla. El foco se queda donde lo deje el sistema.
        }
        // Y el cursor de TalkBack, que no sigue al anterior (ver CursorDeTalkBack.kt).
        movedor.llevarA(if (indice >= 0) etiquetaDeFila(adonde.id!!) else ETIQUETA_LISTA_VACIA)
        // Primero el foco y después el anuncio: mover el foco corta lo que se esté diciendo.
        adonde.anuncio?.let {
            delay(ESPERA_TRAS_CERRAR_MS)
            anuncios.importante(it)
        }
        destino = null
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = Textos.tituloApp },
        topBar = { BarraSuperior(Textos.tituloApp) },
        bottomBar = { LineaDeAvisos(anuncios) },
    ) { margen ->
        Column(Modifier.padding(margen).fillMaxSize()) {
            CampoBusqueda(busqueda)
            FiltroPorEtiqueta(etiqueta, etiquetasDisponibles) { etiqueta = it }

            if (visibles.isEmpty()) {
                Text(
                    Textos.listaVacia(busqueda.text.toString(), etiqueta),
                    modifier =
                        Modifier.padding(16.dp)
                            .focusRequester(focoListaVacia)
                            .focusable()
                            .testTag(ETIQUETA_LISTA_VACIA),
                )
            } else {
                LazyColumn(state = estadoLista, modifier = Modifier.fillMaxSize()) {
                    items(visibles, key = { it.id }) { elemento ->
                        // Cada fila recuerda su foco y lo apunta mientras exista, para poder
                        // volver a ella después de un diálogo. Al salir de la lista se borra.
                        val foco = remember { FocusRequester() }
                        DisposableEffect(elemento.id) {
                            focos[elemento.id] = foco
                            onDispose { focos.remove(elemento.id) }
                        }
                        FilaEnlace(
                            elemento = elemento,
                            foco = foco,
                            alAbrir = { alAbrir(elemento) },
                            alVerDetalles = { alVerDetalles(elemento) },
                            alEditarEtiquetas = { alEditarEtiquetas(elemento) },
                            alCopiar = { alCopiar(elemento) },
                            alPedirEliminar = { aEliminar = elemento },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    aEliminar?.let { elemento ->
        val titulo = Presentacion.titulo(elemento)
        AlertDialog(
            onDismissRequest = {
                aEliminar = null
                destino = DestinoDelFoco(elemento.id, anuncio = null)
            },
            title = { Text(Textos.preguntaEliminar(titulo)) },
            text = { Text(Textos.consecuenciaEliminar(conCuenta)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        aEliminar = null
                        eliminarConCursor(elemento)
                    }
                ) {
                    Text(Textos.eliminar)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        aEliminar = null
                        destino = DestinoDelFoco(elemento.id, anuncio = null)
                    }
                ) {
                    Text(Textos.cancelar)
                }
            },
        )
    }
}

/**
 * Campo basado en estado (`TextFieldState`), no en `value`/`onValueChange`. El de valor tiene
 * problemas de sincronización con el teclado, documentados por Google, de la misma familia que el
 * que duplicaba el texto al dictar en la app de Expo (`CampoBusqueda.tsx`).
 */
@Composable
private fun CampoBusqueda(busqueda: androidx.compose.foundation.text.input.TextFieldState) {
    val teclado = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        state = busqueda,
        label = { Text(Textos.buscar) },
        placeholder = { Text(Textos.marcadorBusqueda) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { teclado?.hide() },
        trailingIcon =
            if (busqueda.text.isNotEmpty()) {
                {
                    IconButton(onClick = { busqueda.clearText() }) {
                        Icon(
                            painterResource(R.drawable.icono_borrar),
                            contentDescription = Textos.borrarBusqueda,
                        )
                    }
                }
            } else null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FiltroPorEtiqueta(
    elegida: String?,
    disponibles: List<String>,
    alElegir: (String?) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(onClick = { abierto = true }) { Text(Textos.filtroPorEtiqueta(elegida)) }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            (listOf(null) + disponibles).forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion ?: Textos.todasLasEtiquetas) },
                    onClick = {
                        abierto = false
                        alElegir(opcion)
                    },
                    modifier = Modifier.semantics { selected = opcion == elegida },
                )
            }
        }
    }
}

/**
 * Una fila es un solo elemento para TalkBack: se lee «título. dominio — etiquetas — fecha» (la
 * opción a de PLAN.md, lo mismo que en el iPhone), tocar dos veces abre, y lo demás está en las
 * acciones de TalkBack.
 *
 * Las acciones salen de un solo sitio, `customActions`. Nada de pulsación larga ni de deslizar para
 * eliminar: la pulsación larga añade «mantener pulsado» a las acciones, que es la versión Android
 * de los gestos que en iOS salían dos veces en el rotor (ver app-ios-nativa/docs/ACCESIBILIDAD.md).
 */
@Composable
fun FilaEnlace(
    elemento: Elemento,
    foco: FocusRequester,
    alAbrir: () -> Unit,
    alVerDetalles: () -> Unit,
    alEditarEtiquetas: () -> Unit,
    alCopiar: () -> Unit,
    alPedirEliminar: () -> Unit,
) {
    // Las mismas cuatro, y en el mismo orden, en las acciones de TalkBack y en el menú de quien
    // mira la pantalla. El orden es el de iOS.
    val acciones =
        listOf(
            Textos.verDetalles to alVerDetalles,
            Textos.editarEtiquetas to alEditarEtiquetas,
            Textos.copiarUrl to alCopiar,
            Textos.eliminar to alPedirEliminar,
        )
    val titulo = Presentacion.titulo(elemento)
    val subtitulo = Presentacion.subtitulo(elemento)
    var menuAbierto by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .testTag(etiquetaDeFila(elemento.id))
                .focusRequester(foco)
                .clickable(onClickLabel = Textos.abrir, onClick = alAbrir)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$titulo. $subtitulo"
                    // En el orden en que se quieren oír. En iOS se declaran al revés porque
                    // VoiceOver las lee al revés; en TalkBack no hay nada documentado, y se mide.
                    customActions = acciones.map { (etiqueta, accion) ->
                        CustomAccessibilityAction(etiqueta) {
                            accion()
                            true
                        }
                    }
                }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // El texto ya está en la descripción de la fila: sin esto se leería dos veces.
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Para quien mira la pantalla: las mismas acciones en un menú. Oculto a TalkBack, que ya
        // las tiene en la fila; si no, cada fila tendría un botón más que recorrer. Oculto también
        // a Voice Access, que lee el mismo árbol.
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

/**
 * Para encontrar la fila y llevarle el cursor de TalkBack. Una `testTag` no se lee en voz alta ni
 * se ve.
 */
fun etiquetaDeFila(id: String) = "fila-$id"

const val ETIQUETA_LISTA_VACIA = "lista-vacia"
