import Foundation

public typealias Cache = [String: Elemento]
public typealias CacheEtiquetas = [String: EtiquetaDefinida]

/// Fusión de lo que hay guardado en el teléfono con lo que responde el
/// servidor. Solo decide qué versión se queda; quién llama por la red vive
/// en la app, no aquí.
public enum Sincronizacion {
    /// Mete en la caché lo que ha llegado de un `GET /sincronizar`.
    ///
    /// El servidor ya resolvió los conflictos entre dispositivos, así que en
    /// principio basta con sobrescribir. La única salvedad es no pisar un
    /// cambio local que sigue en la cola de pendientes y es más reciente que
    /// lo que acaba de llegar: si no, una bajada que se solape con una subida
    /// en curso haría retroceder a la vista un cambio recién hecho, hasta que
    /// la subida lo confirme.
    public static func aplicarPull(
        _ cache: Cache,
        recibidos: [Elemento],
        pendientes: Cache
    ) -> Cache {
        var nueva = cache
        for elemento in recibidos {
            if let pendiente = pendientes[elemento.id],
                pendiente.actualizadoEn > elemento.actualizadoEn
            {
                continue
            }
            nueva[elemento.id] = elemento
        }
        return nueva
    }

    /// El `POST /sincronizar` devuelve la versión definitiva de cada entrada
    /// del lote, que puede no ser la que se mandó si perdió un conflicto. La
    /// caché se sustituye siempre por lo que responde el servidor.
    public static func aplicarRespuestaPush(_ cache: Cache, definitivos: [Elemento]) -> Cache {
        var nueva = cache
        for elemento in definitivos {
            nueva[elemento.id] = elemento
        }
        return nueva
    }

    /// Mismo criterio que `aplicarPull`, para las etiquetas reservadas.
    public static func aplicarPullEtiquetas(
        _ cache: CacheEtiquetas,
        recibidas: [EtiquetaDefinida],
        pendientes: CacheEtiquetas
    ) -> CacheEtiquetas {
        var nueva = cache
        for etiqueta in recibidas {
            if let pendiente = pendientes[etiqueta.id],
                pendiente.actualizadoEn > etiqueta.actualizadoEn
            {
                continue
            }
            nueva[etiqueta.id] = etiqueta
        }
        return nueva
    }

    public static func aplicarRespuestaPushEtiquetas(
        _ cache: CacheEtiquetas,
        definitivas: [EtiquetaDefinida]
    ) -> CacheEtiquetas {
        var nueva = cache
        for etiqueta in definitivas {
            nueva[etiqueta.id] = etiqueta
        }
        return nueva
    }

    /// Los que no están borrados, más recientes primero. Con el
    /// identificador como desempate: dos enlaces guardados en el mismo
    /// milisegundo (importar una biblioteca entera lo hace) saldrían hoy en
    /// un orden y mañana en otro, y la lista se recolocaría sola.
    public static func elementosVisibles(_ cache: Cache) -> [Elemento] {
        cache.values
            .filter { !$0.borrado }
            .sorted {
                $0.actualizadoEn == $1.actualizadoEn
                    ? $0.id < $1.id
                    : $0.actualizadoEn > $1.actualizadoEn
            }
    }

    /// Las etiquetas reservadas que siguen vivas, por nombre.
    public static func etiquetasReservadasVisibles(_ cache: CacheEtiquetas) -> [EtiquetaDefinida] {
        cache.values
            .filter { !$0.borrado }
            .sorted { $0.nombre.localizedCompare($1.nombre) == .orderedAscending }
    }

    /// Cuánto se espera como mínimo entre dos sincronizaciones automáticas.
    /// Volver a la app dispara una, y sin este freno alternar entre dos
    /// aplicaciones sería una petición por cada vuelta.
    public static let intervaloMinimoMs: MarcaDeTiempo = 30_000

    public static func tocaSincronizar(
        ultima: MarcaDeTiempo,
        ahora: MarcaDeTiempo,
        intervalo: MarcaDeTiempo = intervaloMinimoMs
    ) -> Bool {
        ahora - ultima >= intervalo
    }
}
