import Foundation

/// Qué cuenta como enlace guardable, y cómo sacarlo de lo que llega.
///
/// Lo usan la pantalla de añadir, donde la dirección se escribe a mano, y la
/// extensión de compartir, donde llega de otra aplicación. Son el mismo
/// criterio y tiene que seguir siéndolo: un enlace que la pantalla acepta y
/// la extensión rechaza es un fallo que solo se ve al usarlas por separado.
public enum Enlaces {
    /// Se piden `http://` o `https://` por delante y algo detrás.
    ///
    /// Con criterio de portero y no de analizador: una dirección con un
    /// espacio de más o una letra cambiada la rechaza el servidor al pedir
    /// los metadatos, y eso ya está resuelto. Lo que hay que impedir aquí es
    /// guardar «nota para el lunes» como si fuera una página.
    public static func esDireccion(_ texto: String) -> Bool {
        let limpio = texto.trimmingCharacters(in: .whitespacesAndNewlines)
        for esquema in esquemas where limpio.hasPrefix(esquema) {
            return limpio.count > esquema.count
        }
        return false
    }

    /// La dirección que haya dentro de un texto compartido, si la hay.
    ///
    /// Safari entrega la dirección sola, pero muchas aplicaciones comparten
    /// una frase con la dirección metida dentro («Mira esto:
    /// https://ejemplo.com»). Sin esto, compartir desde ellas no guardaba
    /// nada.
    /// Se parte siempre por los espacios, incluso cuando el texto empieza ya
    /// por la dirección: si no, «https://ejemplo.com vía @alguien» se
    /// guardaba entero como si la dirección incluyera la coletilla.
    public static func direccionDentroDe(_ texto: String) -> String? {
        texto
            .split(whereSeparator: \.isWhitespace)
            .first { esDireccion(String($0)) }
            .map(String.init)
    }

    /// La dirección escrita a mano en la pantalla de añadir, con `https://`
    /// delante si no lo trae. `nil` si lo escrito no es una dirección. Las
    /// reglas están en `ANADIR.md`, en la raíz del repositorio, y los casos
    /// en `pruebas-compartidas/direcciones/`, que leen las tres apps.
    ///
    /// Solo para lo escrito a mano: lo compartido sigue pasando por
    /// `direccionDentroDe`, que pide el esquema.
    public static func completar(_ texto: String) -> DireccionEscrita? {
        let limpio = texto.trimmingCharacters(in: .whitespacesAndNewlines)
        let minusculas = limpio.lowercased()
        for esquema in esquemas where minusculas.hasPrefix(esquema) {
            return limpio.count > esquema.count
                ? DireccionEscrita(direccion: limpio, alternativa: nil) : nil
        }
        guard pareceUnSitio(limpio) else {
            return nil
        }
        return DireccionEscrita(direccion: "https://\(limpio)", alternativa: "http://\(limpio)")
    }

    private static func pareceUnSitio(_ texto: String) -> Bool {
        guard !texto.isEmpty, !texto.contains(where: \.isWhitespace), !texto.contains("://")
        else {
            return false
        }
        let sitio = texto.prefix { !"/?#".contains($0) }
        // Un correo con https:// delante abriría el sitio de detrás de la arroba.
        guard !sitio.contains("@"), let punto = sitio.firstIndex(of: ".") else {
            return false
        }
        return punto > sitio.startIndex && sitio.index(after: punto) < sitio.endIndex
    }

    private static let esquemas = ["https://", "http://"]
}

/// Lo que sale de completar una dirección escrita a mano.
public struct DireccionEscrita: Equatable, Sendable {
    public let direccion: String
    /// La misma con `http://`, para probarla si la de `https://` no carga.
    /// Solo cuando el esquema lo ha puesto la app: si lo escribió quien la
    /// usa, se respeta.
    public let alternativa: String?
}
