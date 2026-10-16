package com.jmortizsilva.guardarenlaces

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jmortizsilva.guardarenlaces.dominio.Comprobacion
import com.jmortizsilva.guardarenlaces.dominio.Enlaces
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lo que se abre al compartir con Guárdalo desde otra aplicación. Vive en el mismo proceso que la
 * app y usa su mismo contenedor, así que guardar aquí es lo mismo que guardar desde Añadir: en iOS
 * hizo falta un App Group para esto, aquí no.
 *
 * Tiene su propia tarea y no sale en recientes (ver el manifiesto): al guardar o cancelar se vuelve
 * a la aplicación desde la que se compartió, y si Guárdalo estaba abierto no se le queda esta
 * pantalla encima.
 *
 * Con «Guardar sin preguntar» no enseña nada: guarda sin etiquetas, avisa y se cierra. Por eso es
 * transparente (ver el manifiesto), y la pantalla de compartir pone su propio fondo.
 */
class ActividadCompartir : ComponentActivity() {
    private val contenedor
        get() = (application as Guardalo).contenedor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Si la app ya estaba abierta, sus etiquetas pueden ser de antes de la última
        // sincronización.
        contenedor.modelo.refrescar()
        val url = direccionCompartida(intent)
        // Se lee una vez: si cambiara mientras está abierta, no debe pasar de una forma a otra.
        val sinPreguntar = contenedor.preferencias.guardarSinPreguntar
        setContent {
            Tema {
                when {
                    url == null ->
                        PantallaAvisoCompartir(Textos.compartirNoEsEnlace, alCerrar = ::finish)
                    sinPreguntar -> GuardarSinPreguntar(url)
                    else -> Compartir(url)
                }
            }
        }
    }

    /** Nada en pantalla mientras guarda, salvo si falla: entonces se dice, y se queda abierta. */
    @Composable
    private fun GuardarSinPreguntar(url: String) {
        var fallo by remember { mutableStateOf(false) }
        LaunchedEffect(url) {
            val comprobacion =
                withTimeoutOrNull(ESPERA_COMPROBACION_MS) { contenedor.modelo.comprobar(url) }
            fallo =
                !guardarYCerrar(url, emptyList(), (comprobacion as? Comprobacion.Carga)?.metadatos)
        }
        if (fallo) PantallaAvisoCompartir(Textos.compartirNoSePudo, alCerrar = ::finish)
    }

    /**
     * Guarda, avisa con un Toast, intenta subir y cierra. Devuelve `false` si no se pudo guardar, y
     * entonces no cierra: cerrar perdería el enlace sin remedio.
     *
     * El aviso va antes de subir: el enlace ya está guardado, y lo de la red puede fallar sin que
     * eso cambie. Con un Toast porque la pantalla se va a cerrar, y lo que se diga en ella se
     * perdería con ella; TalkBack lo lee (`docs/ACCESIBILIDAD.md`).
     */
    private suspend fun guardarYCerrar(
        url: String,
        etiquetas: List<String>,
        metadatos: MetadatosExtraidos?,
    ): Boolean {
        val modelo = contenedor.modelo
        val resultado =
            try {
                modelo.guardarSinSubir(url, etiquetas, metadatos)
            } catch (_: Exception) {
                return false
            }
        Toast.makeText(
                applicationContext,
                resultado.anuncio(seComprobo = metadatos != null),
                Toast.LENGTH_LONG,
            )
            .show()
        modelo.subirAhora(LIMITE_SUBIDA_MS)
        finish()
        return true
    }

    @Composable
    private fun Compartir(url: String) {
        val modelo = contenedor.modelo
        // Sus propios avisos: los de la app son de otra pantalla, que puede estar abierta debajo.
        val anuncios = remember { Anuncios() }
        val alcance = rememberCoroutineScope()
        val disponibles by modelo.etiquetasDisponibles.collectAsState()
        val comprobacion = remember { CompletableDeferred<Comprobacion>() }
        var metadatos by remember { mutableStateOf<MetadatosExtraidos?>(null) }
        var comprobando by remember { mutableStateOf(true) }
        var guardando by remember { mutableStateOf(false) }

        // El título se busca nada más abrir, mientras se eligen las etiquetas.
        LaunchedEffect(url) {
            val resultado = modelo.comprobar(url)
            metadatos = (resultado as? Comprobacion.Carga)?.metadatos
            comprobando = false
            comprobacion.complete(resultado)
        }

        fun guardar(etiquetas: List<String>) {
            if (guardando) return
            guardando = true
            alcance.launch {
                // Si todavía no ha terminado, se espera un poco y no más: la dirección viene de
                // otra aplicación y es buena aunque la página no conteste, así que se guarda igual,
                // sin título. Por lo mismo no se pregunta si no carga, como sí hace Añadir.
                if (!comprobacion.isCompleted) anuncios.informativo(Textos.comprobando)
                val sabido = withTimeoutOrNull(ESPERA_COMPROBACION_MS) { comprobacion.await() }
                val datos = (sabido as? Comprobacion.Carga)?.metadatos
                if (!guardarYCerrar(url, etiquetas, datos)) {
                    anuncios.importante(Textos.compartirNoSePudo)
                    guardando = false
                }
            }
        }

        PantallaCompartir(
            url = url,
            metadatos = metadatos,
            comprobando = comprobando,
            disponibles = disponibles,
            guardando = guardando,
            anuncios = anuncios,
            alCancelar = ::finish,
            alGuardar = ::guardar,
        )
    }
}

/**
 * La dirección que llega al compartir, o `null` si lo compartido no lleva ninguna. Chrome manda la
 * dirección sola en `EXTRA_TEXT`; otras aplicaciones, una frase con la dirección dentro.
 */
fun direccionCompartida(intent: Intent): String? {
    if (intent.action != Intent.ACTION_SEND) return null
    val texto = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return null
    return Enlaces.direccionDentroDe(texto)
}

private const val ESPERA_COMPROBACION_MS = 2_000L
private const val LIMITE_SUBIDA_MS = 3_000L
