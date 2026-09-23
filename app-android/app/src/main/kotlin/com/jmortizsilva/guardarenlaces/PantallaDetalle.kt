package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.Presentacion
import com.jmortizsilva.guardarenlaces.dominio.Textos
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Todo lo que se sabe de un enlace, con sus acciones en botones.
 *
 * La lista ya ofrece estas acciones en la fila, pero ahí el título, el dominio y la fecha se oyen
 * de corrido. Aquí cada cosa está en su sitio y se puede recorrer.
 */
@Composable
fun PantallaDetalle(
    elemento: Elemento,
    conCuenta: Boolean,
    anuncios: Anuncios,
    llegada: Llegada?,
    alAtenderLlegada: () -> Unit,
    alVolver: () -> Unit,
    alEditarEtiquetas: () -> Unit,
    alAbrir: () -> Unit,
    alCopiar: () -> Unit,
    alEliminar: () -> Unit,
) {
    var confirmandoEliminar by remember { mutableStateOf(false) }
    val titulo = Presentacion.titulo(elemento)
    val movedor = movedorDeCursor()

    // Al título solo si se llega abriéndola; si se vuelve de las etiquetas, al botón.
    CursorAlTituloAlEntrar(soloSi = llegada == null)
    LaunchedEffect(llegada) {
        val adonde = llegada as? Llegada.ABotonEtiquetas ?: return@LaunchedEffect
        delay(ESPERA_MS)
        movedor.llevarA(ETIQUETA_BOTON_ETIQUETAS)
        adonde.anuncio?.let {
            delay(ESPERA_MS)
            anuncios.importante(it)
        }
        alAtenderLlegada()
    }

    Scaffold(
        topBar = { BarraSuperior(titulo, alVolver) },
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
            // Cada dato con su nombre delante, leído de una vez: «URL: https://…». Sin el nombre,
            // una dirección suelta o una frase sin contexto no se sabe qué es.
            CampoDeSoloLectura(Textos.campoDireccion, elemento.url)
            elemento.descripcion
                ?.takeIf { it.isNotBlank() }
                ?.let { CampoDeSoloLectura(Textos.campoDescripcion, it) }

            val etiquetas = elemento.etiquetas.joinToString(", ").ifEmpty { Textos.ningunaEtiqueta }
            Text(
                "${Textos.campoEtiquetas}: $etiquetas",
                modifier =
                    Modifier.fillMaxWidth()
                        .testTag(ETIQUETA_BOTON_ETIQUETAS)
                        .clickable(
                            onClickLabel = Textos.editarEtiquetas,
                            onClick = alEditarEtiquetas,
                        )
                        .padding(vertical = 8.dp),
            )

            Text(
                Textos.guardadoEl(
                    Presentacion.fechaLegible(
                        elemento.creadoEn,
                        Presentacion.localeDeLaApp,
                        ZoneId.systemDefault(),
                    )
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            OutlinedButton(onClick = alAbrir, modifier = Modifier.fillMaxWidth()) {
                Text(Textos.abrir)
            }
            OutlinedButton(onClick = alCopiar, modifier = Modifier.fillMaxWidth()) {
                Text(Textos.copiarUrl)
            }
            OutlinedButton(
                onClick = { confirmandoEliminar = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(Textos.eliminar, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmandoEliminar) {
        AlertDialog(
            onDismissRequest = { confirmandoEliminar = false },
            title = { Text(Textos.preguntaEliminar(titulo)) },
            text = { Text(Textos.consecuenciaEliminar(conCuenta)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmandoEliminar = false
                        // Se vuelve a la lista y es ella la que elimina: quedarse en la ficha de
                        // algo que ya no existe deja a TalkBack leyendo un fantasma, y la lista es
                        // la que sabe cuál es la fila vecina.
                        alEliminar()
                    }
                ) {
                    Text(Textos.eliminar)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoEliminar = false }) { Text(Textos.cancelar) }
            },
        )
    }
}

/** Un dato con su nombre, que TalkBack lee de una vez. */
@Composable
private fun CampoDeSoloLectura(nombre: String, valor: String) {
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$nombre: $valor" }
    ) {
        Text(nombre, style = MaterialTheme.typography.labelLarge)
        Text(valor)
    }
}

const val ETIQUETA_BOTON_ETIQUETAS = "boton-etiquetas"

private const val ESPERA_MS = 300L
