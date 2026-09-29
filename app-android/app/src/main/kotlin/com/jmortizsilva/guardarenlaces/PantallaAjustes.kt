package com.jmortizsilva.guardarenlaces

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.jmortizsilva.guardarenlaces.dominio.Textos

/**
 * Los ficheros que se ofrecen al elegir. Anchos a propósito: el formato se reconoce por el
 * contenido y no por la extensión (ver `IMPORTAR.md`), y cada aplicación etiqueta los CSV a su
 * manera. Excel, por ejemplo, los marca como hoja de cálculo.
 */
private val tiposImportables = arrayOf("text/*", "application/csv", "application/vnd.ms-excel")

/**
 * Ajustes. De momento con una sola sección, importar enlaces; la cuenta y el guardado silencioso se
 * añadirán como secciones, en el mismo orden que en el iPhone.
 */
@Composable
fun PantallaAjustes(alVolver: () -> Unit, importar: (ByteArray) -> String) {
    val contexto = LocalContext.current
    // Sobrevive a girar el teléfono: el resultado ya está guardado, y perder el cuadro a mitad de
    // leerlo obligaría a importar otra vez para enterarse.
    var resultado by rememberSaveable { mutableStateOf<String?>(null) }

    val elegirFichero =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            // Sin fichero es que se canceló, y no hay nada que contar.
            if (uri == null) return@rememberLauncherForActivityResult
            resultado =
                try {
                    contexto.contentResolver.openInputStream(uri)?.use { importar(it.readBytes()) }
                        ?: Textos.noSePudoAbrirArchivo("")
                } catch (error: Exception) {
                    Textos.noSePudoAbrirArchivo(error.localizedMessage.orEmpty())
                }
        }

    CursorAlTituloAlEntrar()
    Scaffold(topBar = { BarraSuperior(Textos.ajustesTitulo, alVolver = alVolver) }) { margen ->
        Column(Modifier.padding(margen).fillMaxSize()) {
            // Una sola fila con el nombre y la explicación debajo, que TalkBack lee juntos: en
            // Android no hay pista aparte como en iOS, y un segundo elemento suelto sería una
            // parada más que no dice qué hace.
            ListItem(
                headlineContent = { Text(Textos.importarEnlaces) },
                supportingContent = { Text(Textos.pistaImportar) },
                modifier =
                    Modifier.clickable(role = Role.Button) {
                        elegirFichero.launch(tiposImportables)
                    },
            )
        }
    }

    resultado?.let { CuadroResultado(it, alAceptar = { resultado = null }) }
}

/**
 * Un resultado que hay que poder volver a leer, con un único botón. El equivalente del
 * `DialogoAvisoLegible` de Windows y de la hoja del iPhone.
 *
 * Sin título a propósito: lo primero que encuentra TalkBack es el resultado, que es lo que importa,
 * y no el nombre de una acción que se acaba de elegir. Es lo mismo que se hizo en el iPhone
 * llevando allí el foco al texto. Como texto normal, se puede recorrer por palabras o caracteres
 * con los controles de lectura.
 */
@Composable
fun CuadroResultado(texto: String, alAceptar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alAceptar,
        confirmButton = { TextButton(onClick = alAceptar) { Text(Textos.aceptar) } },
        text = { Text(texto) },
    )
}
