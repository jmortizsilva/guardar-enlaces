package com.jmortizsilva.guardarenlaces

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.jmortizsilva.guardarenlaces.dominio.Textos

class ActividadPrincipal : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Con targetSdk 36 el borde a borde es obligatorio; el Scaffold se
        // encarga de dejar sitio a las barras del sistema.
        enableEdgeToEdge()
        setContent { MaterialTheme { PantallaInicial() } }
    }
}

/** Andamiaje de la fase 0: el título y nada más. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicial() {
    Scaffold(
        modifier = Modifier.semantics { paneTitle = Textos.tituloApp },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(Textos.tituloApp, modifier = Modifier.semantics { heading() }) }
            )
        },
    ) { margen ->
        Text("", modifier = Modifier.padding(margen))
    }
}
