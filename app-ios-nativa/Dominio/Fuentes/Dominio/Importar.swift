import Foundation

/// Leer enlaces de un fichero exportado por otra aplicación.
///
/// El comportamiento no se decide aquí: manda `IMPORTAR.md`, en la raíz del
/// repositorio, y vale para los tres clientes. Las pruebas leen los mismos
/// ficheros de ejemplo que las de Windows y Android
/// (`pruebas-compartidas/importacion/`) y comparan contra el mismo
/// `esperado.json`. Si esto se desvía, su prueba falla.
public enum Importar {
    public enum Formato: String, Sendable {
        case html, csv, txt
    }

    public struct EnlaceImportado: Equatable, Sendable {
        public let url: String
        public let titulo: String?
        public let etiquetas: [String]
        /// `nil` es «el fichero no traía fecha»: la pone quien guarde, con su
        /// reloj.
        public let creadoEn: MarcaDeTiempo?
    }

    public struct Lectura: Sendable {
        public let enlaces: [EnlaceImportado]
        public let descartados: Int
        public let formato: Formato
    }

    public struct Preparado: Sendable {
        public let nuevos: [Elemento]
        public let yaEstaban: Int
        public let descartados: Int

        public var importados: Int { nuevos.count }
    }

    // MARK: - Leer

    /// El contenido del fichero, en la codificación que traiga.
    ///
    /// Quien exporta no la elige ni suele saber cuál es. Los navegadores
    /// escriben UTF-8, pero Excel guarda los CSV en la del sistema, y en un
    /// Windows en español eso es cp1252. Latin-1 va al final porque acepta
    /// cualquier byte y nunca falla.
    public static func texto(de datos: Data) -> String {
        if var texto = String(data: datos, encoding: .utf8) {
            // Swift conserva la marca de orden de bytes como un carácter más,
            // y pegada a la cabecera de un CSV hace que no se reconozca.
            if texto.hasPrefix("\u{FEFF}") { texto.removeFirst() }
            return texto
        }
        for codificacion: String.Encoding in [.windowsCP1252, .isoLatin1] {
            if let texto = String(data: datos, encoding: codificacion) { return texto }
        }
        return String(decoding: datos, as: UTF8.self)
    }

    /// Por el contenido y no por la extensión: quien exporta no siempre la
    /// conserva, y un fichero de marcadores llamado .txt sigue siendo HTML.
    public static func detectarFormato(_ contenido: String) -> Formato {
        let principio = String(contenido.drop(while: \.isWhitespace).prefix(400)).lowercased()
        if principio.contains("netscape-bookmark-file") || principio.contains("<dl>")
            || principio.contains("<a href")
        {
            return .html
        }
        if let primera = primeraLineaConAlgo(contenido), separador(primera) != nil {
            return .csv
        }
        return .txt
    }

    public static func leer(_ contenido: String) -> Lectura {
        let formato = detectarFormato(contenido)
        let (crudos, descartados): ([EnlaceImportado], Int)
        switch formato {
        case .html: (crudos, descartados) = leerHTML(contenido)
        case .csv: (crudos, descartados) = leerCSV(contenido)
        case .txt: (crudos, descartados) = leerTXT(contenido)
        }
        let (enlaces, repetidos) = quitarRepetidos(crudos)
        return Lectura(enlaces: enlaces, descartados: descartados + repetidos, formato: formato)
    }

    // MARK: - Qué se guarda de lo leído

    /// Decide qué enlaces hay que guardar. Recibe lo que ya hay y devuelve lo
    /// nuevo, sin tocar la base de datos.
    ///
    /// Un enlace que ya tienes NO se actualiza, al revés que al guardarlo a
    /// mano: importar toca cientos de golpe y sin que los veas pasar, y pisar
    /// títulos y etiquetas puestos a mano no se deshace.
    public static func preparar(
        _ lectura: Lectura,
        existentes: [Elemento],
        ahora: Reloj = relojDelSistema,
        generarId: GeneradorId = generarIdUnico
    ) -> Preparado {
        // Un conjunto con las direcciones ya normalizadas: comparar cada
        // enlace contra la lista entera tardaba segundos en Windows con unos
        // cientos de cada lado.
        var vistas = Set(existentes.filter { !$0.borrado }.map { Duplicados.normalizar($0.url) })
        var nuevos: [Elemento] = []
        var yaEstaban = 0
        let instante = ahora()

        for enlace in lectura.enlaces {
            let clave = Duplicados.normalizar(enlace.url)
            if vistas.contains(clave) {
                yaEstaban += 1
                continue
            }
            vistas.insert(clave)
            nuevos.append(
                Elemento(
                    id: generarId(),
                    url: enlace.url,
                    titulo: enlace.titulo,
                    etiquetas: enlace.etiquetas,
                    // La fecha del fichero manda cuando la trae: si no, los
                    // ochocientos llegan con la de hoy y la lista pierde el
                    // orden.
                    creadoEn: enlace.creadoEn ?? instante,
                    actualizadoEn: instante
                )
            )
        }
        return Preparado(nuevos: nuevos, yaEstaban: yaEstaban, descartados: lectura.descartados)
    }

    // MARK: - Reglas comunes

    /// Solo direcciones de verdad. Los `javascript:` de los bookmarklets y los
    /// `place:` internos de Firefox acabarían en la lista como enlaces que no
    /// abren nada.
    private static let esquemas = ["http://", "https://"]

    static func esDireccion(_ texto: String) -> Bool {
        let minusculas = texto.lowercased()
        return esquemas.contains { minusculas.hasPrefix($0) && minusculas.count > $0.count }
    }

    /// Dentro del propio fichero manda el primero, comparando las direcciones
    /// como en el resto de la aplicación.
    private static func quitarRepetidos(_ enlaces: [EnlaceImportado]) -> ([EnlaceImportado], Int) {
        var vistas = Set<String>()
        var unicos: [EnlaceImportado] = []
        for enlace in enlaces where vistas.insert(Duplicados.normalizar(enlace.url)).inserted {
            unicos.append(enlace)
        }
        return (unicos, enlaces.count - unicos.count)
    }

    /// Sin repetir, y sin que cuenten mayúsculas ni tildes: «Postres» y
    /// «postres» son la misma, y se conserva la primera que apareció.
    static func etiquetasUnicas(_ nombres: [String?]) -> [String] {
        var vistas = Set<String>()
        var salida: [String] = []
        for nombre in nombres {
            guard let limpio = nombre?.trimmingCharacters(in: .whitespacesAndNewlines),
                !limpio.isEmpty,
                vistas.insert(clave(limpio)).inserted
            else { continue }
            salida.append(limpio)
        }
        return salida
    }

    static func clave(_ texto: String) -> String {
        texto.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func primeraLineaConAlgo(_ contenido: String) -> String? {
        contenido.split(whereSeparator: \.isNewline)
            .map(String.init)
            .first { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
    }

    // MARK: - HTML de marcadores

    /// Las carpetas raíz de un fichero de marcadores no distinguen nada: las
    /// lleva todo el mundo. Una etiqueta que tienen cientos de enlaces es ruido.
    private static let carpetasQueNoSonEtiqueta: Set<String> = [
        "bookmarks", "marcadores", "bookmarks bar", "favoritos",
    ]

    /// El formato de Netscape que exportan todos los navegadores.
    ///
    /// Las carpetas son `<H3>` y lo que contienen va dentro del `<DL>` que
    /// viene detrás, no dentro del `<H3>`: el nombre se deja en espera y el
    /// `<DL>` lo mete en una pila.
    private static func leerHTML(_ html: String) -> ([EnlaceImportado], Int) {
        var enlaces: [EnlaceImportado] = []
        var descartados = 0
        var pila: [String?] = []
        var pendiente: String?
        var pendienteEsBarra = false
        var enH3 = false
        var enA = false
        var texto = ""
        var href = ""
        var fecha: MarcaDeTiempo?

        var i = html.startIndex
        while i < html.endIndex {
            guard html[i] == "<" else {
                let siguiente = html[i...].firstIndex(of: "<") ?? html.endIndex
                if enH3 || enA { texto += html[i..<siguiente] }
                i = siguiente
                continue
            }
            if html[i...].hasPrefix("<!--") {
                i = html[i...].range(of: "-->")?.upperBound ?? html.endIndex
                continue
            }
            guard let cierre = html[i...].firstIndex(of: ">") else { break }
            let dentro = html[html.index(after: i)..<cierre]
            i = html.index(after: cierre)

            let cerrando = dentro.hasPrefix("/")
            let nombre = dentro.drop { $0 == "/" }.prefix { $0.isLetter || $0.isNumber }
                .lowercased()
            let atributos = cerrando ? [:] : leerAtributos(String(dentro))

            switch (nombre, cerrando) {
            case ("dl", false):
                pila.append(pendiente)
                pendiente = nil
            case ("dl", true):
                if !pila.isEmpty { pila.removeLast() }
            case ("h3", false):
                enH3 = true
                texto = ""
                // Se reconoce por el atributo y no por el nombre, que cambia
                // con el idioma del navegador.
                pendienteEsBarra = atributos["personal_toolbar_folder"] == "true"
            case ("h3", true):
                let carpeta = decodificar(texto).trimmingCharacters(in: .whitespacesAndNewlines)
                let descartar =
                    pendienteEsBarra || carpetasQueNoSonEtiqueta.contains(clave(carpeta))
                pendiente = descartar ? nil : carpeta
                enH3 = false
                texto = ""
            case ("a", false):
                enA = true
                texto = ""
                href = atributos["href"] ?? ""
                fecha = segundosAMilisegundos(atributos["add_date"])
            case ("a", true):
                enA = false
                let direccion = href.trimmingCharacters(in: .whitespacesAndNewlines)
                let titulo = decodificar(texto).trimmingCharacters(in: .whitespacesAndNewlines)
                texto = ""
                guard !direccion.isEmpty else { continue }
                guard esDireccion(direccion) else {
                    descartados += 1
                    continue
                }
                let carpeta = pila.last { $0 != nil } ?? nil
                enlaces.append(
                    EnlaceImportado(
                        url: direccion,
                        titulo: titulo.isEmpty ? nil : titulo,
                        etiquetas: etiquetasUnicas([carpeta]),
                        creadoEn: fecha
                    )
                )
            default:
                break
            }
        }
        return (enlaces, descartados)
    }

    private static let patronAtributo = try! NSRegularExpression(
        pattern: #"([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))"#
    )

    private static func leerAtributos(_ etiqueta: String) -> [String: String] {
        var atributos: [String: String] = [:]
        let rango = NSRange(etiqueta.startIndex..., in: etiqueta)
        for coincidencia in patronAtributo.matches(in: etiqueta, range: rango) {
            guard let nombre = Range(coincidencia.range(at: 1), in: etiqueta) else { continue }
            let valor =
                (2...4).lazy
                .compactMap { Range(coincidencia.range(at: $0), in: etiqueta) }
                .first
                .map { String(etiqueta[$0]) } ?? ""
            atributos[etiqueta[nombre].lowercased()] = decodificar(valor)
        }
        return atributos
    }

    private static let entidades: [String: String] = [
        "amp": "&", "lt": "<", "gt": ">", "quot": "\"", "apos": "'", "nbsp": "\u{00A0}",
    ]

    /// `&amp;` es `&`. Si no, los títulos llegan con `&amp;` a la lista.
    static func decodificar(_ texto: String) -> String {
        guard texto.contains("&") else { return texto }
        var salida = ""
        var i = texto.startIndex
        while i < texto.endIndex {
            guard texto[i] == "&",
                let fin = texto[i...].prefix(12).firstIndex(of: ";")
            else {
                salida.append(texto[i])
                i = texto.index(after: i)
                continue
            }
            let nombre = String(texto[texto.index(after: i)..<fin])
            if let valor = entidad(nombre) {
                salida += valor
                i = texto.index(after: fin)
            } else {
                salida.append(texto[i])
                i = texto.index(after: i)
            }
        }
        return salida
    }

    private static func entidad(_ nombre: String) -> String? {
        if let conocida = entidades[nombre.lowercased()] { return conocida }
        guard nombre.hasPrefix("#") else { return nil }
        let numero = nombre.dropFirst()
        let valor =
            numero.lowercased().hasPrefix("x")
            ? UInt32(numero.dropFirst(), radix: 16) : UInt32(numero, radix: 10)
        return valor.flatMap(Unicode.Scalar.init).map { String(Character($0)) }
    }

    // MARK: - CSV

    /// Cada aplicación pone las columnas que quiere, así que se buscan por
    /// nombre y no por posición.
    private static let columnas: [String: [String]] = [
        "url": ["url", "link", "direccion", "dirección"],
        "titulo": ["title", "titulo", "título", "name", "nombre"],
        "etiquetas": ["tags", "etiquetas", "labels"],
        "carpeta": ["folder", "carpeta"],
        "fecha": ["time_added", "created", "date", "creado", "fecha"],
    ]

    /// Excel en español separa por punto y coma: la coma es su separador
    /// decimal. Se deduce de la cabecera: el que haga aparecer la columna de
    /// direcciones.
    private static func separador(_ cabecera: String) -> Character? {
        let direcciones = Set(columnas["url", default: []].map(clave))
        for separador: Character in [",", ";", "\t"] where cabecera.contains(separador) {
            let nombres = cabecera.split(separator: separador, omittingEmptySubsequences: false)
            if nombres.contains(where: { direcciones.contains(clave(String($0))) }) {
                return separador
            }
        }
        return nil
    }

    private static func leerCSV(_ contenido: String) -> ([EnlaceImportado], Int) {
        let separador = primeraLineaConAlgo(contenido).flatMap(separador) ?? ","
        let filas = filasCSV(contenido, separador: separador).filter { $0 != [""] }
        guard let cabecera = filas.first else { return ([], 0) }

        func posicion(_ campo: String) -> Int? {
            let buscadas = Set(columnas[campo, default: []].map(clave))
            return cabecera.firstIndex { buscadas.contains(clave($0)) }
        }
        guard let columnaUrl = posicion("url") else { return ([], 0) }
        let columnaTitulo = posicion("titulo")
        let columnaEtiquetas = posicion("etiquetas")
        let columnaCarpeta = posicion("carpeta")
        let columnaFecha = posicion("fecha")

        func valor(_ fila: [String], _ columna: Int?) -> String {
            guard let columna, columna < fila.count else { return "" }
            return fila[columna].trimmingCharacters(in: .whitespacesAndNewlines)
        }

        var enlaces: [EnlaceImportado] = []
        var descartados = 0
        for fila in filas.dropFirst() {
            let url = valor(fila, columnaUrl)
            guard esDireccion(url) else {
                descartados += 1
                continue
            }
            let titulo = valor(fila, columnaTitulo)
            let carpeta = valor(fila, columnaCarpeta)
            enlaces.append(
                EnlaceImportado(
                    url: url,
                    titulo: titulo.isEmpty ? nil : titulo,
                    // La carpeta primero: es la que ordena, y las etiquetas
                    // sueltas vienen detrás.
                    etiquetas: etiquetasUnicas(
                        [hoja(carpeta)] + partirEtiquetas(valor(fila, columnaEtiquetas))
                    ),
                    creadoEn: fechaAMilisegundos(valor(fila, columnaFecha))
                )
            )
        }
        return (enlaces, descartados)
    }

    /// Un CSV de verdad, con comillas: un separador o un salto de línea dentro
    /// de un campo entre comillas es texto, y dos comillas seguidas son una.
    static func filasCSV(_ texto: String, separador: Character) -> [[String]] {
        var filas: [[String]] = []
        var fila: [String] = []
        var campo = ""
        var entreComillas = false
        var i = texto.startIndex

        while i < texto.endIndex {
            let c = texto[i]
            i = texto.index(after: i)
            if entreComillas {
                if c == "\"" {
                    if i < texto.endIndex, texto[i] == "\"" {
                        campo.append("\"")
                        i = texto.index(after: i)
                    } else {
                        entreComillas = false
                    }
                } else {
                    campo.append(c)
                }
            } else if c == "\"" {
                entreComillas = true
            } else if c == separador {
                fila.append(campo)
                campo = ""
            } else if c.isNewline {
                // Ojo: para Swift «\r\n» es un único carácter, así que esto
                // cubre los finales de línea de Windows sin contar dos filas.
                fila.append(campo)
                filas.append(fila)
                fila = []
                campo = ""
            } else {
                campo.append(c)
            }
        }
        if !campo.isEmpty || !fila.isEmpty {
            fila.append(campo)
            filas.append(fila)
        }
        return filas
    }

    /// Pocket separa por `|` y Raindrop por coma. Se aceptan las dos.
    private static func partirEtiquetas(_ texto: String) -> [String?] {
        texto.split { $0 == "|" || $0 == "," }
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    /// De «Recetas/Postres» se queda «Postres», que es la que dice algo.
    private static func hoja(_ ruta: String) -> String? {
        ruta.split(separator: "/").last.map { $0.trimmingCharacters(in: .whitespaces) }
    }

    // MARK: - TXT

    private static func leerTXT(_ contenido: String) -> ([EnlaceImportado], Int) {
        var enlaces: [EnlaceImportado] = []
        var descartados = 0
        for linea in contenido.split(whereSeparator: \.isNewline) {
            guard !linea.trimmingCharacters(in: .whitespaces).isEmpty else { continue }
            // La primera dirección de la línea, aunque venga dentro de una
            // frase: mucha gente guarda «Mira esto: https://…» tal cual.
            guard
                let url = linea.split(whereSeparator: \.isWhitespace).map(String.init).first(
                    where: esDireccion)
            else {
                descartados += 1
                continue
            }
            enlaces.append(EnlaceImportado(url: url, titulo: nil, etiquetas: [], creadoEn: nil))
        }
        return (enlaces, descartados)
    }

    // MARK: - Fechas

    /// `ADD_DATE` y Pocket vienen en segundos; aquí todo va en milisegundos.
    private static func segundosAMilisegundos(_ valor: String?) -> MarcaDeTiempo? {
        guard let valor, !valor.isEmpty, valor.allSatisfy(\.isNumber), let segundos = Int64(valor)
        else { return nil }
        return segundos * 1000
    }

    /// Pocket manda segundos; Raindrop, una fecha ISO.
    private static func fechaAMilisegundos(_ valor: String) -> MarcaDeTiempo? {
        if let segundos = segundosAMilisegundos(valor) { return segundos }
        guard !valor.isEmpty else { return nil }
        for opciones: ISO8601DateFormatter.Options in [
            [.withInternetDateTime, .withFractionalSeconds], [.withInternetDateTime],
        ] {
            let formato = ISO8601DateFormatter()
            formato.formatOptions = opciones
            if let fecha = formato.date(from: valor) {
                return MarcaDeTiempo((fecha.timeIntervalSince1970 * 1000).rounded())
            }
        }
        return nil
    }
}
