package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.delay

/**
 * La barra de arriba de cada pantalla: el título como encabezado y, si se puede volver, «Volver»
 * delante, que es lo primero que se encuentra al recorrerla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                titulo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(ETIQUETA_TITULO).semantics { heading() },
            )
        },
        navigationIcon = {
            if (alVolver != null) {
                IconButton(onClick = alVolver) {
                    Icon(
                        painterResource(R.drawable.icono_volver),
                        contentDescription = Textos.volver,
                    )
                }
            }
        },
        actions = acciones,
    )
}

/**
 * Al abrir una pantalla, el cursor de TalkBack a su título. Sin esto se queda donde estaba, en un
 * elemento que ya no se ve, y lo siguiente que se oye no tiene que ver con la pantalla nueva.
 *
 * Por eso las pantallas que usan esto no llevan `paneTitle`: Android anunciaba el título de la
 * pantalla nueva y después, al llegar el cursor al encabezado, se oía otra vez (medido el
 * 2026-09-23). Con el cursor basta, y queda en la pantalla nueva.
 *
 * Solo al abrirla, no al volver a ella: se decide con lo que hay en la primera composición. Si no,
 * al volver al detalle con el cursor ya puesto en el botón de etiquetas, en cuanto la vuelta se
 * daba por atendida el cursor saltaba al título (medido el 2026-09-23).
 */
@Composable
fun CursorAlTituloAlEntrar(soloSi: Boolean = true) {
    val movedor = movedorDeCursor()
    val alAbrir = remember { soloSi }
    LaunchedEffect(Unit) {
        if (!alAbrir) return@LaunchedEffect
        delay(ESPERA_AL_ENTRAR_MS)
        movedor.llevarA(ETIQUETA_TITULO)
    }
}

const val ETIQUETA_TITULO = "titulo-pantalla"

private const val ESPERA_AL_ENTRAR_MS = 300L
