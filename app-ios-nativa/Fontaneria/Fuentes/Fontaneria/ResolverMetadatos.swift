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

    /// Devuelve `nil` si no se pudo averiguar nada. No lanza a propósito:
    /// guardar el enlace nunca depende de que esto salga bien.
    public func resolver(url: String) async -> MetadatosExtraidos? {
        if await sesion.autenticado {
            return await enElServidor(url: url)
        }
        return await enElTelefono(url: url)
    }

    private func enElServidor(url: String) async -> MetadatosExtraidos? {
        try? await sesion.conReintento { token in
            try await cliente.metadatos(url: url, tokenAcceso: token).comoMetadatos
        }
    }

    private func enElTelefono(url: String) async -> MetadatosExtraidos? {
        // Mismo orden que el servidor: YouTube por su oEmbed, que da título y
        // miniatura más fiables que raspar og:*, y si falla, la página entera.
        if Youtube.esUrlDeYoutube(url), let oembed = await porOEmbedDeYoutube(url: url) {
            return oembed
        }
        guard let html = await descargar(url: url) else {
            return nil
        }
        return Metadatos.extraer(de: html)
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
    private func descargar(url: String) async -> String? {
        guard let direccion = URL(string: url),
            let (datos, respuesta) = try? await sesionHttp.data(from: direccion),
            let http = respuesta as? HTTPURLResponse,
            (200...299).contains(http.statusCode)
        else {
            return nil
        }
        return String(data: datos, encoding: .utf8)
    }
}
