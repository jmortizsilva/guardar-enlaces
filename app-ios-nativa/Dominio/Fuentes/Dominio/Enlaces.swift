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

    private static let esquemas = ["https://", "http://"]
}
