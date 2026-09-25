package com.jmortizsilva.guardarenlaces

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
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
        setContent { Tema { Aplicacion() } }
    }

    /**
     * La pantalla de arriba de la pila. La lista guarda su búsqueda y su filtro mientras se va al
     * detalle y se vuelve (`SaveableStateProvider`): volver y encontrarla vacía obligaría a buscar
     * otra vez lo que se estaba mirando.
     */
    @Composable
    private fun Aplicacion() {
        val modelo = contenedor.modelo
        val navegacion = remember { Navegacion() }
        val estados = rememberSaveableStateHolder()
        var borrador by remember { mutableStateOf(BorradorEnlace()) }
        val elementos by modelo.elementos.collectAsState()
        val etiquetas by modelo.etiquetasDisponibles.collectAsState()

        BackHandler(enabled = navegacion.puedeVolver) { navegacion.volver() }

        when (val pantalla = navegacion.actual) {
            Pantalla.Lista ->
                estados.SaveableStateProvider("lista") {
                    PantallaLista(
                        elementos = elementos,
                        etiquetasDisponibles = etiquetas,
                        conCuenta = modelo.conCuenta,
                        anuncios = modelo.anuncios,
                        alAbrir = ::abrir,
                        alCopiar = ::copiar,
                        alEliminar = modelo::eliminar,
                        alVerDetalles = { navegacion.abrir(Pantalla.Detalle(it.id)) },
                        alEditarEtiquetas = { navegacion.abrir(Pantalla.Etiquetas(it.id)) },
                        alAnadir = {
                            borrador = BorradorEnlace()
                            navegacion.abrir(Pantalla.Anadir)
                        },
                        llegada = navegacion.llegada,
                        alAtenderLlegada = navegacion::llegadaAtendida,
                    )
                }
            is Pantalla.Detalle -> {
                val elemento = elementos.firstOrNull { it.id == pantalla.id }
                if (elemento == null) {
                    // Se eliminó mientras tanto, desde otro sitio: no hay nada que enseñar.
                    LaunchedEffect(pantalla) { navegacion.volver() }
                } else {
                    PantallaDetalle(
                        elemento = elemento,
                        conCuenta = modelo.conCuenta,
                        anuncios = modelo.anuncios,
                        llegada = navegacion.llegada,
                        alAtenderLlegada = navegacion::llegadaAtendida,
                        alVolver = { navegacion.volver() },
                        alEditarEtiquetas = { navegacion.abrir(Pantalla.Etiquetas(elemento.id)) },
                        alAbrir = { abrir(elemento) },
                        alCopiar = { copiar(elemento) },
                        alEliminar = { navegacion.volver(Llegada.EliminarFila(elemento.id)) },
                    )
                }
            }
            Pantalla.Anadir -> {
                // Android solo deja mirar el portapapeles con el foco de la ventana: se mira cuando
                // lo tiene, y no antes.
                val conFoco = LocalWindowInfo.current.isWindowFocused
                val contexto = LocalContext.current
                val copiado =
                    remember(conFoco) {
                        if (conFoco) Portapapeles.queHay(contexto) else Copiado.Nada
                    }
                PantallaAnadir(
                    borrador = borrador,
                    anuncios = modelo.anuncios,
                    copiado = copiado,
                    llegada = navegacion.llegada,
                    alAtenderLlegada = navegacion::llegadaAtendida,
                    repetido = modelo::repetido,
                    comprobar = modelo::comprobar,
                    leerPortapapeles = { Portapapeles.leer(contexto) },
                    alElegirEtiquetas = { navegacion.abrir(Pantalla.EtiquetasDelBorrador) },
                    alCancelar = { navegacion.volver() },
                    alGuardar = { url, elegidas, metadatos ->
                        val resultado = modelo.guardarEnlace(url, elegidas, metadatos)
                        navegacion.volver(
                            Llegada.AFila(
                                resultado.elemento.id,
                                resultado.anuncio(seComprobo = metadatos != null),
                            )
                        )
                    },
                )
            }
            Pantalla.EtiquetasDelBorrador ->
                PantallaEtiquetas(
                    disponibles = etiquetas,
                    elegidasAlEntrar = borrador.etiquetas,
                    anuncios = modelo.anuncios,
                    alCancelar = { navegacion.volver() },
                    alGuardar = {
                        borrador.etiquetas = it
                        navegacion.volver()
                    },
                )
            is Pantalla.Etiquetas -> {
                val elemento = elementos.firstOrNull { it.id == pantalla.id }
                if (elemento == null) {
                    LaunchedEffect(pantalla) { navegacion.volver() }
                } else {
                    PantallaEtiquetas(
                        disponibles = etiquetas,
                        elegidasAlEntrar = elemento.etiquetas,
                        anuncios = modelo.anuncios,
                        alCancelar = { navegacion.volver() },
                        alGuardar = { elegidas ->
                            modelo.cambiarEtiquetas(elemento, elegidas)
                            // Se dice cómo quedan, al llegar adonde se estaba: desde el detalle,
                            // al botón de etiquetas; desde la lista, a la fila.
                            val anuncio = Textos.etiquetasGuardadas(elegidas)
                            val desdeDetalle = navegacion.pila.getOrNull(navegacion.pila.size - 2)
                            navegacion.volver(
                                if (desdeDetalle is Pantalla.Detalle) {
                                    Llegada.ABotonEtiquetas(anuncio)
                                } else {
                                    Llegada.AFila(elemento.id, anuncio)
                                }
                            )
                        },
                    )
                }
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
