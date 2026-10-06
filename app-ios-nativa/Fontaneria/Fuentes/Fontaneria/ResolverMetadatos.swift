import Dominio
import Foundation

/// De dónde salen el título y la descripción de una URL.
///
/// Con cuenta los saca el servidor, que es quien los guarda para los dos
/// clientes; sin cuenta, el propio teléfono descarga la página y la lee.
///
/// Lo que aquí NO hace falta, y en el servidor sí: comprobar que la URL no
/// apunta a una dirección de red privada. Allí es imprescindible porque el
/// servidor descarga una URL que le manda otro y podría alcanzar su red
/// interna; aquí la descarga el propio teléfono, con la URL que ha escrito su
/// dueño, y no llega a ningún sitio al que no llegase ya Safari.
public struct ResolverMetadatos: Sendable {
    private let cliente: ClienteApi
    private let sesion: Sesion
    private let sesionHttp: URLSession

    public init(cliente: ClienteApi, sesion: Sesion, sesionHttp: URLSession = .sesionPorDefecto) {
        self.cliente = cliente
        self.sesion = sesion
        self.sesionHttp = sesionHttp
    }

    /// Título y descripción, o `nil` si no se pudo averiguar nada. Para
    /// compartir desde otra app, donde no se pregunta si la página carga.
    public func resolver(url: String) async -> MetadatosExtraidos? {
        await comprobar(url: url).metadatos
    }

    /// Si la página carga y, de paso, su título y su descripción. No lanza a
    /// propósito: guardar el enlace nunca depende de esto. Ver «Comprobar que
    /// carga» en `ANADIR.md`.
    public func comprobar(url: String) async -> Comprobacion {
        if await sesion.autenticado {
            return await enElServidor(url: url)
        }
        return await enElTelefono(url: url)
    }

    private func enElServidor(url: String) async -> Comprobacion {
        do {
            return .carga(
                try await sesion.conReintento { token in
                    try await cliente.metadatos(url: url, tokenAcceso: token).comoMetadatos
                }
            )
        } catch let error as ErrorApi where error.codigo == 400 || error.codigo == 502 {
            // El servidor sí contestó: la dirección no vale (400) o la página
            // no cargó (502).
            return .noCarga
        } catch {
            // Sin respuesta del servidor, o un fallo suyo: no dice nada de la
            // página.
            return .sinComprobar
        }
    }

    private func enElTelefono(url: String) async -> Comprobacion {
        // Mismo orden que el servidor: YouTube por su oEmbed, que da título y
        // miniatura más fiables que raspar og:*, y si falla, la página entera.
        if Youtube.esUrlDeYoutube(url), let oembed = await porOEmbedDeYoutube(url: url) {
            return .carga(oembed)
        }
        switch await descargar(url: url) {
        case .pagina(let html): return .carga(Metadatos.extraer(de: html))
        case .noCarga: return .noCarga
        case .sinRed: return .sinComprobar
        }
    }

    private func porOEmbedDeYoutube(url: String) async -> MetadatosExtraidos? {
        guard let direccion = Youtube.urlOEmbed(para: url),
            let (datos, respuesta) = try? await sesionHttp.data(from: direccion),
            let http = respuesta as? HTTPURLResponse,
            (200...299).contains(http.statusCode)
        else {
            return nil
        }
        return Youtube.metadatos(desdeOEmbed: datos)
    }

    /// Sin identificarse como nada en particular: algunos sitios responden un
    /// HTML distinto, o un muro, a lo que parece un robot, y aquí interesa
    /// justo lo que vería el navegador.
    private func descargar(url: String) async -> Descarga {
        guard let direccion = URL(string: url) else {
            return .noCarga
        }
        do {
            let (datos, respuesta) = try await sesionHttp.data(from: direccion)
            guard let http = respuesta as? HTTPURLResponse, (200...299).contains(http.statusCode)
            else {
                return .noCarga
            }
            return .pagina(String(decoding: datos, as: UTF8.self))
        } catch let error as URLError where Self.sinRed.contains(error.code) {
            return .sinRed
        } catch {
            return .noCarga
        }
    }

    private enum Descarga {
        case pagina(String)
        case noCarga
        case sinRed
    }

    /// Los errores que dicen que es el teléfono el que no tiene conexión, y no
    /// la página la que falla. A diferencia de Android, aquí el sistema los
    /// distingue de un dominio que no existe (`cannotFindHost`).
    private static let sinRed: Set<URLError.Code> = [
        .notConnectedToInternet, .networkConnectionLost, .dataNotAllowed,
        .internationalRoamingOff,
    ]
}
