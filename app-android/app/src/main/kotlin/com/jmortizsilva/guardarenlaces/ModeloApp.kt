package com.jmortizsilva.guardarenlaces

import com.jmortizsilva.guardarenlaces.dominio.Biblioteca
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.Sincronizacion
import com.jmortizsilva.guardarenlaces.fontaneria.AlmacenLocal
import com.jmortizsilva.guardarenlaces.fontaneria.ErrorApi
import com.jmortizsilva.guardarenlaces.fontaneria.Sesion
import com.jmortizsilva.guardarenlaces.fontaneria.Sincronizador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lo que ven y piden las pantallas: los enlaces, las etiquetas y los cambios sobre ellos.
 *
 * El almacén se lee en el hilo principal, como en iOS: SQLite local tarda microsegundos, y volver
 * asíncrona cada lectura complicaría todo sin ganar nada. La red, en cambio, nunca bloquea: la
 * sincronización va en una corrutina.
 */
class ModeloApp(
    private val almacen: AlmacenLocal,
    private val sesion: Sesion,
    private val sincronizador: Sincronizador,
    val anuncios: Anuncios,
    private val alcance: CoroutineScope = MainScope(),
) {
    private val _elementos = MutableStateFlow<List<Elemento>>(emptyList())

    /** Los enlaces no borrados, más recientes primero. */
    val elementos: StateFlow<List<Elemento>> = _elementos.asStateFlow()

    private val _etiquetasDisponibles = MutableStateFlow<List<String>>(emptyList())

    /** Las que lleva algún enlace más las reservadas. */
    val etiquetasDisponibles: StateFlow<List<String>> = _etiquetasDisponibles.asStateFlow()

    val conCuenta: Boolean
        get() = sesion.conCuenta

    init {
        refrescar()
    }

    fun refrescar() {
        val visibles = Sincronizacion.elementosVisibles(almacen.cargarTodos())
        val reservadas =
            Sincronizacion.etiquetasReservadasVisibles(almacen.cargarEtiquetasDefinidas()).map {
                it.nombre
            }
        _elementos.value = visibles
        _etiquetasDisponibles.value = Biblioteca.etiquetasDisponibles(visibles, reservadas)
    }

    /**
     * Deja la lápida y la encola. No anuncia nada: quien llama mueve antes el foco a la fila
     * siguiente, y el anuncio va después. Al revés, el cambio de foco cortaría el anuncio a medias
     * (lo documenta la app de Expo, `foco.ts`).
     */
    fun eliminar(elemento: Elemento) {
        almacen.marcarPendiente(elemento.marcadoComoBorrado())
        refrescar()
        sincronizarEnSilencio()
    }

    /**
     * La automática: se calla pase lo que pase. El cambio ya está en la cola local y se reintenta
     * en la siguiente; avisar de cada fallo de red sería ruido constante.
     */
    fun sincronizarEnSilencio() {
        if (!sesion.conCuenta) return
        alcance.launch {
            try {
                sincronizador.sincronizar()
            } catch (_: ErrorApi) {}
            refrescar()
        }
    }
}
