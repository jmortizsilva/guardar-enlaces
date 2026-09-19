import Dominio
import Fontaneria
import Foundation
import Observation

/// El estado de la hoja que sale al compartir un enlace.
///
/// Abre la misma base de datos que la aplicación y guarda con el mismo
/// código: aquí no se decide nada distinto, solo se hace desde otro proceso.
@MainActor
@Observable
final class ModeloCompartir {
    let url: String
    private(set) var metadatos: MetadatosExtraidos?
    private(set) var comprobando = true
    private(set) var etiquetasDisponibles: [String] = []
    var etiquetasPuestas: Set<String> = []

    /// El título que se enseña: el de la página si ya se sabe, y si no, la
    /// dirección. Nunca un hueco vacío, que con VoiceOver es una fila muda.
    var titulo: String {
        metadatos?.titulo ?? url
    }

    private let almacen: AlmacenLocal
    private let sesion: Sesion
    private let resolvedor: ResolverMetadatos
    private let sincronizador: Sincronizador

    init(url: String) throws {
        self.url = url
        almacen = try AlmacenLocal(ruta: try Configuracion.rutaBaseDatos().path)
        let cliente = ClienteApi(urlBase: Configuracion.urlApi)
        sesion = Sesion(cliente: cliente, credenciales: CredencialesKeychain())
        resolvedor = ResolverMetadatos(
            cliente: cliente,
            sesion: sesion,
            sesionHttp: Self.sesionHttpCorta
        )
        sincronizador = Sincronizador(almacen: almacen, cliente: cliente, sesion: sesion)
        etiquetasDisponibles = Self.etiquetas(de: almacen)
    }

    /// Una extensión no vive mucho: si el sistema la mata a medio guardar, el
    /// enlace se pierde sin que nadie diga nada. Se corta pronto y se guarda
    /// sin título, que es mejor que no guardar.
    private static let sesionHttpCorta: URLSession = {
        let ajustes = URLSessionConfiguration.ephemeral
        ajustes.timeoutIntervalForRequest = 5
        ajustes.timeoutIntervalForResource = 5
        return URLSession(configuration: ajustes)
    }()

    private static func etiquetas(de almacen: AlmacenLocal) -> [String] {
        let enUso = Biblioteca.etiquetasEnUso(
            Sincronizacion.elementosVisibles((try? almacen.cargarTodos()) ?? [:])
        )
        let reservadas =
            (try? almacen.cargarEtiquetasDefinidas())
            .map(Sincronizacion.etiquetasReservadasVisibles)?
            .map(\.nombre) ?? []
        return Set(enUso + reservadas).sorted { $0.localizedCompare($1) == .orderedAscending }
    }

    /// Recupera la sesión y busca el título. Ninguna de las dos cosas hace
    /// falta para guardar.
    func comprobar() async {
        _ = await sesion.restaurar()
        metadatos = await resolvedor.resolver(url: url)
        comprobando = false
    }

    /// Guarda el enlace y devuelve lo que hay que decir en voz alta, o `nil`
    /// si no se pudo guardar.
    func guardar() -> String? {
        guard
            let resultado = try? GuardarEnlace.guardar(
                url: url,
                etiquetas: etiquetasPuestas.sorted(),
                metadatos: metadatos,
                en: almacen
            )
        else {
            return nil
        }
        return resultado.anuncio(seComprobo: metadatos != nil)
    }

    /// Intenta subirlo ya, para que esté en el ordenador sin tener que abrir
    /// la aplicación. Si no hay cuenta o no hay red, da igual: queda encolado
    /// y sube en la siguiente sincronización.
    func intentarSubir() async {
        _ = try? await sincronizador.sincronizar()
    }
}
