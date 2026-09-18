import Dominio
import Foundation

/// Un ciclo completo de sincronización: primero sube lo que hay pendiente,
/// después baja lo que haya cambiado en el servidor.
///
/// Cómo fusionar lo decide `Dominio`; aquí solo se encadenan almacén, cliente
/// y sesión.
///
/// Vive en el hilo principal porque comparte el almacén con la interfaz, que
/// lo lee para pintar la lista. Las esperas de red son asíncronas, así que no
/// bloquean nada: lo único que ocurre en el hilo principal son las consultas
/// a SQLite, que tardan microsegundos.
@MainActor
public final class Sincronizador {
    private let almacen: AlmacenLocal
    private let cliente: ClienteApi
    private let sesion: Sesion

    public init(almacen: AlmacenLocal, cliente: ClienteApi, sesion: Sesion) {
        self.almacen = almacen
        self.cliente = cliente
        self.sesion = sesion
    }

    /// Devuelve cuántos cambios locales rechazó el servidor (normalmente cero).
    @discardableResult
    public func sincronizar() async throws -> Int {
        let rechazados = try await subirPendientes()
        try await bajarCambios()
        return rechazados
    }

    private func subirPendientes() async throws -> Int {
        let elementos = Array(try almacen.cargarPendientes().values)
        let etiquetas = Array(try almacen.cargarEtiquetasPendientes().values)
        guard !elementos.isEmpty || !etiquetas.isEmpty else {
            return 0
        }

        let respuesta = try await sesion.conReintento { token in
            try await cliente.subirCambios(
                elementos: elementos,
                etiquetas: etiquetas,
                tokenAcceso: token
            )
        }

        try almacen.guardar(
            Sincronizacion.aplicarRespuestaPush(
                try almacen.cargarTodos(),
                definitivos: respuesta.elementos
            )
        )
        try almacen.guardarEtiquetasDefinidas(
            Sincronizacion.aplicarRespuestaPushEtiquetas(
                try almacen.cargarEtiquetasDefinidas(),
                definitivas: respuesta.etiquetasDefinidas
            )
        )

        // Lo rechazado sale de la cola igual que lo aceptado. El servidor no
        // lo va a admitir por mucho que se insista, y dejarlo dentro reenvía
        // el lote entero en cada sincronización, para siempre y sin que se
        // note: así se quedaron atascados los enlaces importados a una cuenta
        // nueva.
        try almacen.limpiarPendientes(
            respuesta.elementos.map(\.id) + respuesta.rechazados.map(\.id)
        )
        try almacen.limpiarEtiquetasPendientes(
            respuesta.etiquetasDefinidas.map(\.id) + respuesta.etiquetasRechazadas.map(\.id)
        )

        return respuesta.rechazados.count + respuesta.etiquetasRechazadas.count
    }

    private func bajarCambios() async throws {
        // Se repite mientras el servidor diga que quedan páginas: biblioteca
        // grande o primera sincronización.
        while true {
            let desde = try almacen.cursor()
            let respuesta = try await sesion.conReintento { token in
                try await cliente.bajarCambios(desde: desde, tokenAcceso: token)
            }

            try almacen.guardar(
                Sincronizacion.aplicarPull(
                    try almacen.cargarTodos(),
                    recibidos: respuesta.elementos,
                    pendientes: try almacen.cargarPendientes()
                )
            )
            try almacen.guardarEtiquetasDefinidas(
                Sincronizacion.aplicarPullEtiquetas(
                    try almacen.cargarEtiquetasDefinidas(),
                    recibidas: respuesta.etiquetasDefinidas,
                    pendientes: try almacen.cargarEtiquetasPendientes()
                )
            )

            if respuesta.masDisponible, !respuesta.elementos.isEmpty {
                try almacen.fijarCursor(respuesta.elementos.map(\.actualizadoEn).max() ?? 0)
            } else {
                try almacen.fijarCursor(respuesta.servidorEn)
                return
            }
        }
    }
}
