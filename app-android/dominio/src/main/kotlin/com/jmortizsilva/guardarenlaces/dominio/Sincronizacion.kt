package com.jmortizsilva.guardarenlaces.dominio

typealias Cache = Map<String, Elemento>

typealias CacheEtiquetas = Map<String, EtiquetaDefinida>

/**
 * Fusión de lo que hay guardado en el teléfono con lo que responde el servidor. Solo decide qué
 * versión se queda; quién llama por la red vive en la fontanería, no aquí.
 */
object Sincronizacion {
    /**
     * Mete en la caché lo que ha llegado de un `GET /sincronizar`.
     *
     * El servidor ya resolvió los conflictos entre dispositivos, así que en principio basta con
     * sobrescribir. La única salvedad es no pisar un cambio local que sigue en la cola de
     * pendientes y es más reciente que lo que acaba de llegar: si no, una bajada que se solape con
     * una subida en curso haría retroceder a la vista un cambio recién hecho, hasta que la subida
     * lo confirme.
     */
    fun aplicarPull(cache: Cache, recibidos: List<Elemento>, pendientes: Cache): Cache =
        fusionar(cache, recibidos, pendientes, Elemento::id, Elemento::actualizadoEn)

    /**
     * El `POST /sincronizar` devuelve la versión definitiva de cada entrada del lote, que puede no
     * ser la que se mandó si perdió un conflicto. La caché se sustituye siempre por lo que responde
     * el servidor.
     */
    fun aplicarRespuestaPush(cache: Cache, definitivos: List<Elemento>): Cache =
        cache + definitivos.associateBy { it.id }

    /** Mismo criterio que `aplicarPull`, para las etiquetas reservadas. */
    fun aplicarPullEtiquetas(
        cache: CacheEtiquetas,
        recibidas: List<EtiquetaDefinida>,
        pendientes: CacheEtiquetas,
    ): CacheEtiquetas =
        fusionar(
            cache,
            recibidas,
            pendientes,
            EtiquetaDefinida::id,
            EtiquetaDefinida::actualizadoEn,
        )

    fun aplicarRespuestaPushEtiquetas(
        cache: CacheEtiquetas,
        definitivas: List<EtiquetaDefinida>,
    ): CacheEtiquetas = cache + definitivas.associateBy { it.id }

    private fun <T> fusionar(
        cache: Map<String, T>,
        recibidos: List<T>,
        pendientes: Map<String, T>,
        id: (T) -> String,
        fecha: (T) -> MarcaDeTiempo,
    ): Map<String, T> {
        val nueva = cache.toMutableMap()
        for (recibido in recibidos) {
            val pendiente = pendientes[id(recibido)]
            if (pendiente != null && fecha(pendiente) > fecha(recibido)) continue
            nueva[id(recibido)] = recibido
        }
        return nueva
    }

    /**
     * Los que no están borrados, los guardados más recientemente primero.
     *
     * Por fecha de guardado y no de última modificación: si no, cambiarle una etiqueta a un enlace
     * de hace un mes lo mandaba al principio de la lista, y el orden dejaba de tener que ver con lo
     * que se ve escrito en cada fila.
     *
     * Con el identificador como desempate: dos enlaces guardados en el mismo milisegundo (importar
     * una biblioteca entera lo hace) saldrían hoy en un orden y mañana en otro, y la lista se
     * recolocaría sola.
     */
    fun elementosVisibles(cache: Cache): List<Elemento> =
        cache.values
            .filter { !it.borrado }
            .sortedWith(compareByDescending<Elemento> { it.creadoEn }.thenBy { it.id })

    /** Las etiquetas reservadas que siguen vivas, por nombre. */
    fun etiquetasReservadasVisibles(cache: CacheEtiquetas): List<EtiquetaDefinida> =
        cache.values.filter { !it.borrado }.sortedWith(compareBy(ordenAlfabetico) { it.nombre })

    /**
     * Cuánto se espera como mínimo entre dos sincronizaciones automáticas. Volver a la app dispara
     * una, y sin este freno alternar entre dos aplicaciones sería una petición por cada vuelta.
     */
    const val INTERVALO_MINIMO_MS: MarcaDeTiempo = 30_000

    fun tocaSincronizar(
        ultima: MarcaDeTiempo,
        ahora: MarcaDeTiempo,
        intervalo: MarcaDeTiempo = INTERVALO_MINIMO_MS,
    ): Boolean = ahora - ultima >= intervalo
}
