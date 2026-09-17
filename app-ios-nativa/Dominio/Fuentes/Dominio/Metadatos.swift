import Foundation

public struct MetadatosExtraidos: Equatable, Sendable {
    public let titulo: String?
    public let descripcion: String?
    public let imagenUrl: String?
    public let tipo: TipoElemento

    public init(
        titulo: String? = nil,
        descripcion: String? = nil,
        imagenUrl: String? = nil,
        tipo: TipoElemento = .enlace
    ) {
        self.titulo = titulo
        self.descripcion = descripcion
        self.imagenUrl = imagenUrl
        self.tipo = tipo
    }
}

/// Lee los metadatos Open Graph de un HTML ya descargado: un par de
/// expresiones regulares tolerantes, sin librería de análisis.
///
/// Esto es una COPIA de `backend/src/metadatos/extraccion.ts`. Los proyectos
/// del repositorio no comparten código (ver PROYECTO.md), así que se duplica
/// a propósito: si cambia el criterio de qué es un vídeo o un artículo, hay
/// que tocarlo EN LOS DOS SITIOS, o el mismo enlace saldrá distinto según se
/// guarde con cuenta (lo resuelve el servidor) o sin ella (lo resuelve el
/// teléfono).
public enum Metadatos {
    /// Solo estas cinco, las mismas que el backend. `&eacute;` y compañía se
    /// quedan sin traducir, y eso se ve en el título. No se arregla aquí a
    /// solas: hacerlo solo en el teléfono es justo lo que haría que el mismo
    /// enlace se viera distinto según quién resolvió sus metadatos.
    private static let entidades = [
        ("&amp;", "&"),
        ("&lt;", "<"),
        ("&gt;", ">"),
        ("&quot;", "\""),
        ("&#39;", "'"),
    ]

    private static func decodificarEntidades(_ texto: String) -> String {
        entidades.reduce(texto) { resultado, entidad in
            resultado.replacingOccurrences(of: entidad.0, with: entidad.1)
        }
    }

    private static func primerGrupo(_ patron: String, en texto: String) -> String? {
        guard
            let expresion = try? NSRegularExpression(pattern: patron, options: [.caseInsensitive]),
            let coincidencia = expresion.firstMatch(
                in: texto,
                range: NSRange(texto.startIndex..., in: texto)
            ),
            let rango = Range(coincidencia.range(at: 1), in: texto)
        else {
            return nil
        }
        return String(texto[rango])
    }

    /// El orden de `property` y `content` cambia según el sitio, así que se
    /// prueban las dos formas.
    private static func metaOpenGraph(_ html: String, propiedad: String) -> String? {
        let patrones = [
            "<meta[^>]+property=[\"']og:\(propiedad)[\"'][^>]+content=[\"']([^\"']*)[\"']",
            "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:\(propiedad)[\"']",
        ]
        for patron in patrones {
            if let valor = primerGrupo(patron, en: html) {
                return decodificarEntidades(valor)
            }
        }
        return nil
    }

    private static func tituloDeLaPestana(_ html: String) -> String? {
        guard let bruto = primerGrupo("<title[^>]*>([^<]*)</title>", en: html) else {
            return nil
        }
        let limpio = decodificarEntidades(bruto).trimmingCharacters(in: .whitespacesAndNewlines)
        return limpio.isEmpty ? nil : limpio
    }

    private static func tipo(segunOpenGraph ogType: String?) -> TipoElemento {
        guard let ogType else {
            return .enlace
        }
        if ogType.contains("video") {
            return .video
        }
        if ogType.contains("article") {
            return .articulo
        }
        if ogType.contains("image") || ogType.contains("photo") {
            return .imagen
        }
        return .enlace
    }

    public static func extraer(de html: String) -> MetadatosExtraidos {
        MetadatosExtraidos(
            titulo: metaOpenGraph(html, propiedad: "title") ?? tituloDeLaPestana(html),
            descripcion: metaOpenGraph(html, propiedad: "description"),
            imagenUrl: metaOpenGraph(html, propiedad: "image"),
            tipo: tipo(segunOpenGraph: metaOpenGraph(html, propiedad: "type"))
        )
    }
}

/// YouTube aparte: su oEmbed público da título y miniatura más fiables que
/// raspar `og:*`, y no necesita credenciales. COPIA de
/// `backend/src/metadatos/youtube.ts` (ver la nota de arriba).
public enum Youtube {
    private static let dominios: Set<String> = [
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
    ]

    public static func esUrlDeYoutube(_ url: String) -> Bool {
        guard let host = URLComponents(string: url)?.host else {
            return false
        }
        return dominios.contains(host.lowercased())
    }

    /// Los caracteres que `encodeURIComponent` deja pasar, que es lo que usan
    /// el backend y la app de Expo para montar esta misma dirección.
    private static let sinEscapar = CharacterSet(
        charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    )

    /// La dirección se monta a mano en vez de con `URLComponents.queryItems`
    /// porque aquel deja sin escapar los dos puntos y las barras de la URL
    /// del vídeo. Aquí acaba dentro de un parámetro, así que se escapa entera.
    public static func urlOEmbed(para url: String) -> URL? {
        guard let escapada = url.addingPercentEncoding(withAllowedCharacters: sinEscapar) else {
            return nil
        }
        return URL(string: "https://www.youtube.com/oembed?url=\(escapada)&format=json")
    }

    /// Lee la respuesta del oEmbed. Si no se entiende, devuelve nil y quien
    /// llama cae al raspado genérico, que es lo que hace el backend.
    public static func metadatos(desdeOEmbed datos: Data) -> MetadatosExtraidos? {
        struct RespuestaOEmbed: Decodable {
            let title: String?
            let thumbnailUrl: String?

            enum CodingKeys: String, CodingKey {
                case title
                case thumbnailUrl = "thumbnail_url"
            }
        }

        guard let respuesta = try? JSONDecoder().decode(RespuestaOEmbed.self, from: datos) else {
            return nil
        }
        return MetadatosExtraidos(
            titulo: respuesta.title,
            descripcion: nil,
            imagenUrl: respuesta.thumbnailUrl,
            tipo: .video
        )
    }
}
