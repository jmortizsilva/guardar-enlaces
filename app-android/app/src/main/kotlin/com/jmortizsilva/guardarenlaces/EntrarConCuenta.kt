package com.jmortizsilva.guardarenlaces

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jmortizsilva.guardarenlaces.dominio.EnlacesEnElTelefono
import com.jmortizsilva.guardarenlaces.dominio.Login
import com.jmortizsilva.guardarenlaces.dominio.Proveedor
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Cómo se entra: abrir el navegador y, si hay enlaces en el teléfono, preguntar qué hacer. */
typealias Entrar = suspend (Proveedor, suspend (EnlacesEnElTelefono) -> Boolean) -> Login.Resultado

/**
 * Entrar con una cuenta desde una pantalla: lo comparten Ajustes y la bienvenida. Mientras se
 * entra, `ocupada` apaga los botones; si hay enlaces en el teléfono, la pregunta sale con
 * `PreguntaEnlacesEnElTelefono` y la entrada espera a la respuesta.
 */
class EntradaConCuenta(private val entrar: Entrar, private val alcance: CoroutineScope) {
    var ocupada by mutableStateOf(false)
        private set

    var pregunta by mutableStateOf<EnlacesEnElTelefono?>(null)
        private set

    private var respuesta: CompletableDeferred<Boolean>? = null

    fun entrarCon(proveedor: Proveedor, alTerminar: suspend (Login.Resultado) -> Unit) {
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
            alTerminar(salida)
        }
    }

    fun responder(importar: Boolean) {
        pregunta = null
        respuesta?.complete(importar)
        respuesta = null
    }
}

@Composable
fun rememberEntradaConCuenta(entrar: Entrar): EntradaConCuenta {
    val alcance = rememberCoroutineScope()
    return remember { EntradaConCuenta(entrar, alcance) }
}

/** Qué hacer con los enlaces que ya había en el teléfono al entrar. */
@Composable
fun PreguntaEnlacesEnElTelefono(entrada: EntradaConCuenta) {
    val enlaces = entrada.pregunta ?: return
    // Sin cancelar: hay que elegir, y las dos respuestas son definitivas.
    AlertDialog(
        onDismissRequest = {},
        title = { TituloDeDialogo(Textos.tituloEnlacesEnElTelefono) },
        text = { Text(Textos.preguntaImportar(enlaces.cuantos, enlaces.deOtraCuenta)) },
        confirmButton = {
            TextButton(onClick = { entrada.responder(true) }) { Text(Textos.anadirlos) }
        },
        dismissButton = {
            TextButton(onClick = { entrada.responder(false) }) { Text(Textos.borrarlos) }
        },
    )
}
