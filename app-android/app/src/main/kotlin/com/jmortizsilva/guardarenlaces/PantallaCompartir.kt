package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.Presentacion
import com.jmortizsilva.guardarenlaces.dominio.Textos

/**
 * Lo que sale al compartir un enlace desde otra aplicación. Es la pantalla de añadir recortada a lo
 * que hace falta aquí: la dirección ya viene dada, así que quedan las etiquetas y los dos botones.
 * La misma que la hoja de compartir del iPhone.
 */
@Composable
fun PantallaCompartir(
    url: String,
    metadatos: MetadatosExtraidos?,
    comprobando: Boolean,
    disponibles: List<String>,
    guardando: Boolean,
    anuncios: Anuncios,
    alCancelar: () -> Unit,
    alGuardar: (List<String>) -> Unit,
) {
    val eleccion = recordarEleccionDeEtiquetas(disponibles, emptyList())

    CursorAlTituloAlEntrar()
    Scaffold(
        topBar = {
            BarraSuperior(Textos.compartirTitulo) {
                TextButton(onClick = alCancelar) { Text(Textos.cancelar) }
                TextButton(onClick = { alGuardar(eleccion.marcadas()) }, enabled = !guardando) {
                    Text(Textos.guardar)
                }
            }
        },
        bottomBar = { LineaDeAvisos(anuncios) },
    ) { margen ->
        LazyColumn(Modifier.padding(margen).fillMaxSize()) {
            item {
                val titulo = metadatos?.titulo ?: Textos.compartirSinTitulo
                // Título y sitio, una sola parada. Se lee el sitio y no la dirección entera, que
                // en voz alta es larga y no dice nada que el sitio no diga ya.
                val sitio = Presentacion.dominio(url) ?: url
                Column(
                    Modifier.fillMaxWidth().padding(16.dp).clearAndSetSemantics {
                        contentDescription = "$titulo. $sitio"
                    }
                ) {
                    Text(titulo, style = MaterialTheme.typography.titleMedium)
                    Text(
                        url,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (comprobando) {
                item { Text(Textos.comprobando, Modifier.padding(horizontal = 16.dp)) }
            }
            item {
                Text(
                    Textos.compartirEtiquetas,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp).semantics { heading() },
                )
            }
            if (eleccion.todas.isEmpty()) {
                item { Text(Textos.compartirSinEtiquetas, Modifier.padding(horizontal = 16.dp)) }
            }
            casillasDeEtiquetas(eleccion)
        }
    }
}

/** Cuando lo compartido no se puede guardar. Un solo botón, y el texto dice por qué. */
@Composable
fun PantallaAvisoCompartir(mensaje: String, alCerrar: () -> Unit) {
    CursorAlTituloAlEntrar()
    Scaffold(topBar = { BarraSuperior(Textos.compartirTitulo) }) { margen ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(margen).fillMaxSize().padding(16.dp),
        ) {
            Text(mensaje)
            Button(onClick = alCerrar) { Text(Textos.cerrar) }
        }
    }
}
