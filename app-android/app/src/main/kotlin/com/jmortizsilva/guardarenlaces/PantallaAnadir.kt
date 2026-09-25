package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.Enlaces
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lo que se está escribiendo en «Añadir enlace». Vive fuera de la pantalla para no perderse al ir a
 * elegir las etiquetas y volver.
 */
class BorradorEnlace {
    val url = TextFieldState()
    var etiquetas by mutableStateOf(emptyList<String>())
}

/**
 * Pegar o escribir una URL y guardarla.
 *
 * La comprobación va sola en segundo plano y rellena la vista previa, pero nunca hace falta
 * esperarla: guardar funciona en cuanto la dirección es válida. Si la comprobación no llega a
 * tiempo, el enlace se guarda sin título y se dice. Es lo mismo que hacen iOS y Windows.
 */
@Composable
fun PantallaAnadir(
    borrador: BorradorEnlace,
    anuncios: Anuncios,
    copiado: Copiado,
    llegada: Llegada?,
    alAtenderLlegada: () -> Unit,
    repetido: (String) -> Elemento?,
    comprobar: suspend (String) -> MetadatosExtraidos?,
    leerPortapapeles: () -> String?,
    alElegirEtiquetas: () -> Unit,
    alCancelar: () -> Unit,
    alGuardar: (url: String, etiquetas: List<String>, metadatos: MetadatosExtraidos?) -> Unit,
) {
    val movedor = movedorDeCursor()
    val alcance = rememberCoroutineScope()
    val focoCampo = remember { FocusRequester() }
    var vistaPrevia by remember { mutableStateOf<MetadatosExtraidos?>(null) }
    var comprobacion by remember { mutableStateOf<Deferred<MetadatosExtraidos?>?>(null) }
    var comprobando by remember { mutableStateOf(false) }
    /**
     * Desde que se pulsa Guardar hasta que la pantalla se cierra. Sin esto, guardar un enlace nuevo
     * avisaba de que ya lo tenías: entra en la lista antes de que se cierre esto, y al recomponerse
     * se encuentra a sí mismo. Lo cuenta iOS.
     */
    var guardando by remember { mutableStateOf(false) }

    val urlLimpia = borrador.url.text.toString().trim()
    val urlValida = Enlaces.esDireccion(urlLimpia)
    val yaGuardado = if (urlValida && !guardando) repetido(urlLimpia) else null

    // Al abrir, al campo; al volver de las etiquetas, a su botón.
    val alAbrir = remember { llegada == null }
    LaunchedEffect(Unit) {
        if (!alAbrir) return@LaunchedEffect
        focoCampo.requestFocus()
        delay(ESPERA_MS)
        movedor.llevarA(ETIQUETA_CAMPO_URL)
    }
    LaunchedEffect(llegada) {
        if (llegada !is Llegada.ABotonEtiquetas) return@LaunchedEffect
        delay(ESPERA_MS)
        movedor.llevarA(ETIQUETA_BOTON_ETIQUETAS)
        alAtenderLlegada()
    }

    // El botón de pegar aparece solo si hay algo que pegar, y un botón que aparece por su cuenta
    // hay que contarlo, o es como si no existiera para quien no mira la pantalla. Con un respiro:
    // en iOS, dicho justo al abrir, se perdía.
    val hayQuePegar = copiado != Copiado.Nada && urlLimpia.isEmpty()
    LaunchedEffect(copiado) {
        if (copiado == Copiado.Nada || !alAbrir) return@LaunchedEffect
        delay(ESPERA_AVISO_COPIADO_MS)
        anuncios.importante(
            if (copiado == Copiado.Enlace) Textos.hayEnlaceCopiado else Textos.hayTextoCopiado
        )
    }

    // Cambiar la dirección invalida lo comprobado: era de otra página. Se vuelve a comprobar con
    // un respiro, para no lanzar una petición por cada tecla al escribir a mano.
    LaunchedEffect(borrador) {
        snapshotFlow { borrador.url.text.toString().trim() }
            .distinctUntilChanged()
            .collect { direccion ->
                comprobacion?.cancel()
                vistaPrevia = null
                comprobando = Enlaces.esDireccion(direccion)
                comprobacion =
                    if (!comprobando) null
                    else
                        alcance.async {
                            delay(ESPERA_COMPROBAR_MS)
                            comprobar(direccion).also {
                                vistaPrevia = it
                                comprobando = false
                            }
                        }
            }
    }

    // Se dice en cuanto se detecta, no al guardar: enterarse después no sirve de nada.
    LaunchedEffect(yaGuardado?.id) {
        if (yaGuardado != null) anuncios.importante(Textos.enlaceRepetido)
    }

    fun guardar() {
        if (!urlValida || guardando) return
        guardando = true
        val enMarcha = comprobacion
        alcance.launch {
            // Lo que se sepa de la página ahora. Si la comprobación sigue en marcha, un respiro
            // corto y nada más: un sitio que no contesta tarda diez segundos en rendirse, y dejar
            // la pantalla clavada sin decir nada es justo lo que no puede pasar.
            val metadatos =
                vistaPrevia
                    ?: enMarcha?.let {
                        withTimeoutOrNull(ESPERA_MAXIMA_AL_GUARDAR_MS) { it.await() }
                    }
            alGuardar(urlLimpia, borrador.etiquetas, metadatos)
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior(Textos.anadirEnlaceTitulo) {
                TextButton(onClick = alCancelar) { Text(Textos.cancelar) }
                TextButton(onClick = ::guardar, enabled = urlValida && !guardando) {
                    Text(if (yaGuardado == null) Textos.guardar else Textos.actualizar)
                }
            }
        },
        bottomBar = { LineaDeAvisos(anuncios) },
    ) { margen ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier.padding(margen)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            OutlinedTextField(
                state = borrador.url,
                label = { Text(Textos.campoUrl) },
                placeholder = { Text(Textos.marcadorUrl) },
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = urlLimpia.isNotEmpty() && !urlValida,
                supportingText =
                    if (urlLimpia.isNotEmpty() && !urlValida) {
                        { Text(Textos.urlNoValida) }
                    } else null,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                onKeyboardAction = { guardar() },
                modifier =
                    Modifier.fillMaxWidth().focusRequester(focoCampo).testTag(ETIQUETA_CAMPO_URL),
            )

            if (hayQuePegar) {
                OutlinedButton(
                    onClick = {
                        leerPortapapeles()?.let {
                            borrador.url.setTextAndPlaceCursorAtEnd(Enlaces.paraPegar(it))
                        }
                    }
                ) {
                    Text(Textos.pegar)
                }
            }

            OutlinedButton(
                onClick = alElegirEtiquetas,
                modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_BOTON_ETIQUETAS),
            ) {
                Text(Textos.botonEtiquetas(borrador.etiquetas))
            }

            if (comprobando) Text(Textos.comprobando)

            vistaPrevia?.titulo?.let { titulo ->
                // Título y descripción de una vez, como se leería la fila en la lista.
                val descripcion = vistaPrevia?.descripcion
                Column(
                    Modifier.fillMaxWidth().clearAndSetSemantics {
                        contentDescription = listOfNotNull(titulo, descripcion).joinToString(". ")
                    }
                ) {
                    Text(titulo, style = MaterialTheme.typography.titleMedium)
                    descripcion?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (yaGuardado != null) Text(Textos.enlaceRepetido)
        }
    }
}

const val ETIQUETA_CAMPO_URL = "campo-url"

private const val ESPERA_MS = 300L
private const val ESPERA_AVISO_COPIADO_MS = 900L
private const val ESPERA_COMPROBAR_MS = 500L
private const val ESPERA_MAXIMA_AL_GUARDAR_MS = 2_000L
