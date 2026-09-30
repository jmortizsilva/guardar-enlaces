package com.jmortizsilva.guardarenlaces

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jmortizsilva.guardarenlaces.ModeloApp.EstadoCuenta
import com.jmortizsilva.guardarenlaces.dominio.EnlacesEnElTelefono
import com.jmortizsilva.guardarenlaces.dominio.Login
import com.jmortizsilva.guardarenlaces.dominio.Proveedor
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Los ficheros que se ofrecen al elegir. Anchos a propósito: el formato se reconoce por el
 * contenido y no por la extensión (ver `IMPORTAR.md`), y cada aplicación etiqueta los CSV a su
 * manera. Excel, por ejemplo, los marca como hoja de cálculo.
 */
private val tiposImportables = arrayOf("text/*", "application/csv", "application/vnd.ms-excel")

/** El texto que dice con qué cuenta se está. Adonde va el cursor al entrar y al salir. */
const val ETIQUETA_CUENTA = "texto-cuenta"

/**
 * Ajustes: la cuenta, e importar enlaces. Falta el guardado silencioso, que irá con el menú de
 * compartir, en el mismo orden que en el iPhone.
 */
@Composable
fun PantallaAjustes(
    cuenta: EstadoCuenta,
    anuncios: Anuncios,
    alVolver: () -> Unit,
    importar: (ByteArray) -> String,
    entrar: suspend (Proveedor, suspend (EnlacesEnElTelefono) -> Boolean) -> Login.Resultado,
    cerrarSesion: suspend () -> Unit,
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val movedor = movedorDeCursor()
    // Sobrevive a girar el teléfono: el resultado ya está guardado, y perder el cuadro a mitad de
    // leerlo obligaría a repetir para enterarse.
    var resultado by rememberSaveable { mutableStateOf<String?>(null) }
    var ocupada by remember { mutableStateOf(false) }
    var pregunta by remember { mutableStateOf<EnlacesEnElTelefono?>(null) }
    var respuesta by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }

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

    fun entrarCon(proveedor: Proveedor) {
        alcance.launch {
            ocupada = true
            val salida =
                entrar(proveedor) { enlaces ->
                    val espera = CompletableDeferred<Boolean>()
                    respuesta = espera
                    pregunta = enlaces
                    espera.await()
                }
            ocupada = false
            when (salida) {
                // El botón que se pulsó ya no está: el cursor, al texto que dice con qué cuenta
                // se ha entrado. No se anuncia además, o se oiría lo mismo dos veces.
                is Login.Resultado.Exito -> movedor.llevarA(ETIQUETA_CUENTA)
                is Login.Resultado.Error -> resultado = salida.mensaje
                // Lo ha hecho quien usa la app, y al volver TalkBack ya lee esta pantalla.
                Login.Resultado.Cancelado -> Unit
            }
        }
    }

    CursorAlTituloAlEntrar()
    Scaffold(
        topBar = { BarraSuperior(Textos.ajustesTitulo, alVolver = alVolver) },
        bottomBar = { LineaDeAvisos(anuncios) },
    ) { margen ->
        Column(Modifier.padding(margen).fillMaxSize()) {
            // Una opción es una sola fila con el nombre y la explicación debajo, que TalkBack lee
            // juntos: en Android no hay pista aparte como en iOS, y un segundo elemento suelto
            // sería una parada más que no dice qué hace.
            when (cuenta) {
                is EstadoCuenta.ConCuenta -> {
                    Text(
                        cuenta.email?.let(Textos::sesionIniciadaComo)
                            ?: Textos.sesionIniciadaSinCorreo,
                        Modifier.padding(16.dp).testTag(ETIQUETA_CUENTA),
                    )
                    Opcion(Textos.cerrarSesion, Textos.pistaCerrarSesion, activa = !ocupada) {
                        alcance.launch {
                            ocupada = true
                            cerrarSesion()
                            ocupada = false
                            movedor.llevarA(ETIQUETA_CUENTA)
                        }
                    }
                }
                EstadoCuenta.SinCuenta -> {
                    Text(Textos.sinCuenta, Modifier.padding(16.dp).testTag(ETIQUETA_CUENTA))
                    Opcion(Textos.entrarConGoogle, Textos.pistaLogin, activa = !ocupada) {
                        entrarCon(Proveedor.Google)
                    }
                    Opcion(Textos.entrarConApple, Textos.pistaLogin, activa = !ocupada) {
                        entrarCon(Proveedor.Apple)
                    }
                }
            }
            HorizontalDivider()
            Opcion(Textos.importarEnlaces, Textos.pistaImportar) {
                elegirFichero.launch(tiposImportables)
            }
        }
    }

    pregunta?.let { enlaces ->
        // Sin cancelar: hay que elegir, y las dos respuestas son definitivas.
        fun responder(importar: Boolean) {
            pregunta = null
            respuesta?.complete(importar)
            respuesta = null
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(Textos.tituloEnlacesEnElTelefono) },
            text = { Text(Textos.preguntaImportar(enlaces.cuantos, enlaces.deOtraCuenta)) },
            confirmButton = {
                TextButton(onClick = { responder(true) }) { Text(Textos.anadirlos) }
            },
            dismissButton = {
                TextButton(onClick = { responder(false) }) { Text(Textos.borrarlos) }
            },
        )
    }

    resultado?.let { CuadroResultado(it, alAceptar = { resultado = null }) }
}

@Composable
private fun Opcion(
    nombre: String,
    explicacion: String,
    activa: Boolean = true,
    alPulsar: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(nombre) },
        supportingContent = { Text(explicacion) },
        modifier = Modifier.clickable(enabled = activa, role = Role.Button, onClick = alPulsar),
    )
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
