import Foundation

/// Los textos con los que se muestra un enlace en la lista.
///
/// Están aquí, y no en la vista, porque son contrato: la fila compone su
/// nombre accesible con ellos, así que el subtítulo tiene que valer igual
/// leído en voz alta que visto. Cambiarlos es cambiar lo que oye quien usa
/// VoiceOver, y por eso tienen pruebas.
public enum Presentacion {
    /// Español fijo, no el idioma del teléfono: la app está entera en
    /// español y una fecha en inglés en mitad de una frase en español se
    /// nota más leída que vista.
    public static let localeDeLaApp = Locale(identifier: "es_ES")

    public static func titulo(de elemento: Elemento) -> String {
        let titulo = elemento.titulo ?? ""
        return titulo.isEmpty ? elemento.url : titulo
    }

    public static func subtitulo(
        de elemento: Elemento,
        locale: Locale = localeDeLaApp,
        zonaHoraria: TimeZone = .current
    ) -> String {
        var partes: [String] = []

        if let dominio = dominio(de: elemento.url) {
            partes.append(dominio)
        }
        if !elemento.etiquetas.isEmpty {
            partes.append(elemento.etiquetas.joined(separator: ", "))
        }
        partes.append(
            fechaLegible(elemento.actualizadoEn, locale: locale, zonaHoraria: zonaHoraria)
        )

        return partes.joined(separator: " — ")
    }

    private static func dominio(de url: String) -> String? {
        guard let host = URLComponents(string: url)?.host, !host.isEmpty else {
            return nil
        }
        return host
    }

    /// El mes en letra a propósito: «15/3/2024» VoiceOver lo lee dígito a
    /// dígito, y hay que descifrarlo en vez de oírlo.
    public static func fechaLegible(
        _ instante: MarcaDeTiempo,
        locale: Locale,
        zonaHoraria: TimeZone
    ) -> String {
        guard instante != 0 else {
            return "sin fecha"
        }
        let formato = DateFormatter()
        formato.locale = locale
        formato.timeZone = zonaHoraria
        formato.dateStyle = .long
        formato.timeStyle = .none
        return formato.string(from: Date(timeIntervalSince1970: Double(instante) / 1000))
    }
}
