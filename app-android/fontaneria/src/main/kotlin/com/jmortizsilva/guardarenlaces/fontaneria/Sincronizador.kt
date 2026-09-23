package com.jmortizsilva.guardarenlaces.fontaneria

import com.jmortizsilva.guardarenlaces.dominio.Sincronizacion

/**
 * Un ciclo completo de sincronización: primero sube lo que hay pendiente, después baja lo que haya
 * cambiado en el servidor.
 *
 * Cómo fusionar lo decide `dominio`; aquí solo se encadenan almacén, cliente y sesión. Cada fusión
 * se guarda en una transacción: leer, fusionar y escribir de golpe, para que un cambio que haga la
 * pantalla entretanto no se quede a medias entre una lectura y una escritura.
 */
class Sincronizador(
    private val almacen: AlmacenLocal,
    private val cliente: ClienteApi,
    private val sesion: Sesion,
) {
    /** Devuelve cuántos cambios locales rechazó el servidor (normalmente cero). */
    suspend fun sincronizar(): Int {
        val rechazados = subirPendientes()
        bajarCambios()
        return rechazados
    }

    private suspend fun subirPendientes(): Int {
        val elementos = almacen.cargarPendientes().values.toList()
        val etiquetas = almacen.cargarEtiquetasPendientes().values.toList()
        if (elementos.isEmpty() && etiquetas.isEmpty()) return 0

        val respuesta = sesion.conReintento { token ->
            cliente.subirCambios(elementos, etiquetas, token)
        }

        almacen.enTransaccion {
            almacen.guardar(
                Sincronizacion.aplicarRespuestaPush(almacen.cargarTodos(), respuesta.elementos)
            )
            almacen.guardarEtiquetasDefinidas(
                Sincronizacion.aplicarRespuestaPushEtiquetas(
                    almacen.cargarEtiquetasDefinidas(),
                    respuesta.etiquetasDefinidas,
                )
            )
            // Lo rechazado sale de la cola igual que lo aceptado. El servidor no lo va a admitir
            // por mucho que se insista, y dejarlo dentro reenvía el lote entero en cada
            // sincronización, para siempre y sin que se note: así se quedaron atascados en iOS
            // los enlaces importados a una cuenta nueva.
            almacen.limpiarPendientes(
                respuesta.elementos.map { it.id } + respuesta.rechazados.map { it.id }
            )
            almacen.limpiarEtiquetasPendientes(
                respuesta.etiquetasDefinidas.map { it.id } +
                    respuesta.etiquetasRechazadas.map { it.id }
            )
        }
        return respuesta.rechazados.size + respuesta.etiquetasRechazadas.size
    }

    private suspend fun bajarCambios() {
        // Se repite mientras el servidor diga que quedan páginas: biblioteca grande o primera
        // sincronización.
        while (true) {
            val desde = almacen.cursor()
            val respuesta = sesion.conReintento { token -> cliente.bajarCambios(desde, token) }

            val terminado = !respuesta.masDisponible || respuesta.elementos.isEmpty()
            almacen.enTransaccion {
                almacen.guardar(
                    Sincronizacion.aplicarPull(
                        almacen.cargarTodos(),
                        respuesta.elementos,
                        almacen.cargarPendientes(),
                    )
                )
                almacen.guardarEtiquetasDefinidas(
                    Sincronizacion.aplicarPullEtiquetas(
                        almacen.cargarEtiquetasDefinidas(),
                        respuesta.etiquetasDefinidas,
                        almacen.cargarEtiquetasPendientes(),
                    )
                )
                // El cursor, en la misma transacción que lo que ha bajado: si la app se cierra a
                // mitad, o se guardan las dos cosas o ninguna, y la próxima vez se pide lo mismo.
                almacen.fijarCursor(
                    if (terminado) respuesta.servidorEn
                    else respuesta.elementos.maxOf { it.actualizadoEn }
                )
            }
            if (terminado) return
        }
    }
}
