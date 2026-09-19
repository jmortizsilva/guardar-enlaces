import Foundation

/// Saber si una URL ya está guardada.
///
/// Comparar las URL tal cual no sirve: la misma página llega con «www» o sin
/// él, con http o con https, con una barra final de más, con un ancla, o
/// arrastrando los parámetros de seguimiento que añaden las redes sociales y
/// los boletines. Todo eso es el mismo enlace para una persona, que es quien
/// va a oír el aviso.
///
/// Lo que no se toca: las mayúsculas de la ruta (hay servidores donde sí
/// distinguen) ni los parámetros que de verdad identifican el contenido.
///
/// Es duplicado solo dentro de la biblioteca de cada uno: que otra persona
/// tenga guardado el mismo enlace no pinta nada aquí.
public enum Duplicados {
    private static let prefijosDeSeguimiento = ["utm_"]
    private static let parametrosDeSeguimiento: Set<String> = [
        "fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "ref", "ref_src", "si",
    ]

    private static func esDeSeguimiento(_ clave: String) -> Bool {
        let minuscula = clave.lowercased()
        return parametrosDeSeguimiento.contains(minuscula)
            || prefijosDeSeguimiento.contains { minuscula.hasPrefix($0) }
    }

    /// Forma canónica para comparar, no para guardar ni para abrir: se queda
    /// sin esquema a propósito, porque http y https son la misma página.
    ///
    /// Lo que no se puede analizar se devuelve tal cual en minúsculas: mejor
    /// no detectar un duplicado que inventarse uno.
    public static func normalizar(_ url: String) -> String {
        let limpia = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let partes = URLComponents(string: limpia),
            let anfitrion = partes.host?.lowercased(),
            !anfitrion.isEmpty
        else {
            return limpia.lowercased()
        }

        let host = anfitrion.hasPrefix("www.") ? String(anfitrion.dropFirst(4)) : anfitrion
        var ruta = partes.path
        while ruta.hasSuffix("/") {
            ruta.removeLast()
        }

        // Se ordena por nombre para que dé igual en qué orden vinieran, pero
        // conservando el orden original entre los que repiten nombre: `sorted`
        // no promete ser estable, y dos parámetros con la misma clave podrían
        // salir hoy en un orden y mañana en otro.
        let parametros =
            (partes.queryItems ?? [])
            .enumerated()
            .filter { !esDeSeguimiento($0.element.name) }
            .sorted { izquierda, derecha in
                izquierda.element.name == derecha.element.name
                    ? izquierda.offset < derecha.offset
                    : izquierda.element.name < derecha.element.name
            }
            .map { "\($0.element.name)=\($0.element.value ?? "")" }
            .joined(separator: "&")

        return parametros.isEmpty ? "\(host)\(ruta)" : "\(host)\(ruta)?\(parametros)"
    }

    public static func esLaMisma(_ una: String, _ otra: String) -> Bool {
        normalizar(una) == normalizar(otra)
    }

    /// El elemento ya guardado con esa misma URL, si lo hay. Los borrados no
    /// cuentan: si lo tiraste, volver a guardarlo es un alta normal.
    public static func buscar(en elementos: [Elemento], url: String) -> Elemento? {
        let buscada = normalizar(url)
        return elementos.first { !$0.borrado && normalizar($0.url) == buscada }
    }
}
