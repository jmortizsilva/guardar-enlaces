package com.jmortizsilva.guardarenlaces

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.Textos

class ActividadPrincipal : ComponentActivity() {
    private val contenedor
        get() = (application as Guardalo).contenedor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Con targetSdk 36 el borde a borde es obligatorio; el Scaffold deja sitio a las barras.
        enableEdgeToEdge()
        atender(intent)
        val modelo = contenedor.modelo
        setContent {
            Tema {
                val elementos by modelo.elementos.collectAsState()
                val etiquetas by modelo.etiquetasDisponibles.collectAsState()
                PantallaLista(
                    elementos = elementos,
                    etiquetasDisponibles = etiquetas,
                    conCuenta = modelo.conCuenta,
                    anuncios = modelo.anuncios,
                    alAbrir = ::abrir,
                    alCopiar = ::copiar,
                    alEliminar = modelo::eliminar,
                )
            }
        }
    }

    /** Con la app ya abierta, lo que llega nuevo entra por aquí y no por `onCreate`. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        atender(intent)
    }

    private fun atender(intent: Intent) {
        if (intent.getBooleanExtra(Ejemplos.EXTRA, false)) {
            Ejemplos.reponer(contenedor.almacen)
            contenedor.modelo.refrescar()
        }
    }

    override fun onResume() {
        super.onResume()
        // Lo que se haya guardado mientras la app estaba detrás (más adelante, desde el menú de
        // compartir) tiene que aparecer al volver.
        contenedor.modelo.refrescar()
    }

    /**
     * En una pestaña del navegador dentro de la app. Android no tiene un modo lector que se pueda
     * pedir desde aquí, por eso el botón dice «Abrir» y no «Abrir en modo lector» como en iOS.
     */
    private fun abrir(elemento: Elemento) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, elemento.url.toUri())
        } catch (_: ActivityNotFoundException) {
            // Sin ningún navegador instalado no hay dónde abrirlo. No pasa en un teléfono normal.
        }
    }

    private fun copiar(elemento: Elemento) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(Textos.campoDireccion, elemento.url))
        // Desde Android 13 el sistema ya dice «Texto copiado» por su cuenta. Medido en el
        // teléfono: con el nuestro se oían los dos seguidos. Google recomienda lo mismo: avisar
        // solo en Android 12 o anterior.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            contenedor.anuncios.importante(Textos.urlCopiada)
        }
    }
}
