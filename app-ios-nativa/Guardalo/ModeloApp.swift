import Dominio
import Fontaneria
import Observation
import SwiftUI

/// Cablea almacén, cliente, sesión y sincronizador, y se los da a las
/// pantallas. Es el equivalente de `ProveedorApp.tsx` en la app de Expo.
///
/// Vive en el hilo principal, como el almacén que comparte con la interfaz.
@MainActor
@Observable
final class ModeloApp {
    /// Los enlaces no borrados, más recientes primero.
    private(set) var elementos: [Elemento] = []
    private(set) var sincronizando = false
    private(set) var autenticado = false
    private(set) var usuario: UsuarioApi?
    /// Mientras se intenta recuperar una sesión guardada no se sabe aún si
    /// esto va a ir en local o con cuenta.
    private(set) var arrancando = true

    private let almacen: AlmacenLocal
    private let cliente: ClienteApi
    private let sesion: Sesion
    private let sincronizador: Sincronizador
    /// Para no sincronizar dos veces seguidas al alternar entre aplicaciones.
    private var ultimaSincronizacion: MarcaDeTiempo = 0

    init() throws {
        almacen = try AlmacenLocal(ruta: try AlmacenLocal.rutaPorDefecto())
        cliente = ClienteApi(urlBase: Configuracion.urlApi)
        sesion = Sesion(cliente: cliente, credenciales: CredencialesKeychain())
        sincronizador = Sincronizador(almacen: almacen, cliente: cliente, sesion: sesion)
    }

    /// Solo para las vistas previas de Xcode y las pruebas de interfaz.
    init(enMemoriaCon elementos: [Elemento]) throws {
        almacen = try AlmacenLocal(ruta: ":memory:")
        cliente = ClienteApi(urlBase: "https://api.ejemplo.com")
        sesion = Sesion(cliente: cliente, credenciales: CredencialesKeychain())
        sincronizador = Sincronizador(almacen: almacen, cliente: cliente, sesion: sesion)
        for elemento in elementos {
            try almacen.guardar([elemento.id: elemento])
        }
        arrancando = false
        refrescar()
    }

    // MARK: - Arranque

    func arrancar() async {
        // La lista se lee siempre: sin cuenta es lo único que hay.
        refrescar()
        autenticado = await sesion.restaurar()
        usuario = await sesion.usuario
        arrancando = false
        if autenticado {
            await sincronizarEnSilencio()
        }
    }

    /// Volver a la app trae lo que se haya guardado en el PC mientras tanto.
    /// Sin esto solo se enteraba al arrastrar la lista, y «lo guardé en el
    /// otro sitio y aquí no está» es de las cosas que más desconfianza dan.
    func alVolverAPrimerPlano() async {
        guard Sincronizacion.tocaSincronizar(ultima: ultimaSincronizacion, ahora: relojDelSistema())
        else {
            return
        }
        await sincronizarEnSilencio()
    }

    /// Las que ya lleva algún enlace más las reservadas, que existen aunque
    /// todavía no las lleve ninguno.
    var etiquetasDisponibles: [String] {
        let enUso = Biblioteca.etiquetasEnUso(elementos)
        let reservadas =
            (try? almacen.cargarEtiquetasDefinidas())
            .map(Sincronizacion.etiquetasReservadasVisibles)?
            .map(\.nombre) ?? []
        return Set(enUso + reservadas).sorted { $0.localizedCompare($1) == .orderedAscending }
    }

    private func refrescar() {
        elementos = (try? almacen.cargarTodos()).map(Sincronizacion.elementosVisibles) ?? []
    }

    // MARK: - Acciones sobre la lista

    func eliminar(_ elemento: Elemento) {
        try? almacen.marcarPendiente(elemento.marcadoComoBorrado())
        refrescar()
        Anuncios.importante(Textos.eliminado(titulo: Presentacion.titulo(de: elemento)))
        Task { await sincronizarEnSilencio() }
    }

    func cambiarEtiquetas(de elemento: Elemento, a etiquetas: [String]) {
        try? almacen.marcarPendiente(elemento.conEtiquetas(etiquetas))
        refrescar()
        Anuncios.importante(Textos.etiquetasGuardadas(etiquetas))
        Task { await sincronizarEnSilencio() }
    }

    // MARK: - Sincronizar

    /// La que pide el usuario arrastrando la lista: dice cómo ha acabado,
    /// igual que la app de Windows al pulsar F5.
    func sincronizarAMano() async {
        guard autenticado else {
            return
        }
        let antes = Set(elementos.map(\.id))
        do {
            try await ejecutarSincronizacion()
            let nuevos = Set(elementos.map(\.id)).subtracting(antes).count
            Anuncios.importante(Textos.sincronizacionTerminada(enlacesNuevos: nuevos))
        } catch let fallo as ErrorApi {
            Anuncios.importante(Textos.sincronizacionFallida(causa: fallo.mensaje))
        } catch {
            Anuncios.importante(Textos.sincronizacionFallida(causa: "\(error)"))
        }
    }

    /// La automática: se calla pase lo que pase. El cambio ya está en la cola
    /// local y se reintenta en la siguiente; avisar de cada fallo de red sería
    /// ruido constante y no hay nada que decidir al oírlo.
    func sincronizarEnSilencio() async {
        guard autenticado else {
            return
        }
        try? await ejecutarSincronizacion()
    }

    private func ejecutarSincronizacion() async throws {
        ultimaSincronizacion = relojDelSistema()
        sincronizando = true
        defer { sincronizando = false }
        try await sincronizador.sincronizar()
        refrescar()
    }
}
