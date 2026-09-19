import Foundation
import SQLite3

/// Lo justo de SQLite para lo que hace esta app: abrir, ejecutar y consultar.
///
/// Envoltorio propio en vez de GRDB: son cuatro tablas, seis consultas y
/// ningún modelo que mapear. Traer una librería entera para esto sería
/// pagar una dependencia grande a cambio de ahorrar cien líneas.
final class BaseDatos {
    /// SQLite necesita saber si el texto que se le pasa va a seguir vivo
    /// después de la llamada. Con `TRANSIENT` se queda con su propia copia,
    /// que es lo correcto cuando el texto es una cadena de Swift que puede
    /// desaparecer en cuanto termine la función.
    private static let textoTransitorio = unsafeBitCast(
        -1,
        to: sqlite3_destructor_type.self
    )

    enum Fallo: Error, CustomStringConvertible {
        case noSePudoAbrir(String)
        case consulta(String)

        var description: String {
            switch self {
            case .noSePudoAbrir(let detalle): "no se pudo abrir la base de datos: \(detalle)"
            case .consulta(let detalle): "fallo en la base de datos: \(detalle)"
            }
        }
    }

    private var conexion: OpaquePointer?

    init(ruta: String) throws {
        let banderas = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
        guard sqlite3_open_v2(ruta, &conexion, banderas, nil) == SQLITE_OK else {
            let detalle = conexion.map { String(cString: sqlite3_errmsg($0)) } ?? "desconocido"
            sqlite3_close_v2(conexion)
            conexion = nil
            throw Fallo.noSePudoAbrir(detalle)
        }
    }

    deinit {
        sqlite3_close_v2(conexion)
    }

    func cerrar() {
        sqlite3_close_v2(conexion)
        conexion = nil
    }

    private func ultimoError() -> String {
        conexion.map { String(cString: sqlite3_errmsg($0)) } ?? "sin conexión"
    }

    /// Para el esquema y los borrados en bloque: varias sentencias de una vez,
    /// sin parámetros.
    func ejecutar(_ sql: String) throws {
        guard sqlite3_exec(conexion, sql, nil, nil, nil) == SQLITE_OK else {
            throw Fallo.consulta(ultimoError())
        }
    }

    /// Una sentencia con parámetros, de las que no devuelven filas.
    func ejecutar(_ sql: String, _ parametros: [ValorSql]) throws {
        let sentencia = try preparar(sql, parametros)
        defer { sqlite3_finalize(sentencia) }
        let resultado = sqlite3_step(sentencia)
        guard resultado == SQLITE_DONE || resultado == SQLITE_ROW else {
            throw Fallo.consulta(ultimoError())
        }
    }

    /// Una consulta, fila a fila. `leer` recibe cada fila ya envuelta.
    func consultar<T>(
        _ sql: String,
        _ parametros: [ValorSql] = [],
        leer: (Fila) -> T
    ) throws -> [T] {
        let sentencia = try preparar(sql, parametros)
        defer { sqlite3_finalize(sentencia) }
        var filas: [T] = []
        while true {
            let resultado = sqlite3_step(sentencia)
            if resultado == SQLITE_DONE { break }
            guard resultado == SQLITE_ROW else {
                throw Fallo.consulta(ultimoError())
            }
            filas.append(leer(Fila(sentencia: sentencia)))
        }
        return filas
    }

    private func preparar(_ sql: String, _ parametros: [ValorSql]) throws -> OpaquePointer? {
        var sentencia: OpaquePointer?
        guard sqlite3_prepare_v2(conexion, sql, -1, &sentencia, nil) == SQLITE_OK else {
            let fallo = Fallo.consulta(ultimoError())
            sqlite3_finalize(sentencia)
            throw fallo
        }
        for (indice, valor) in parametros.enumerated() {
            let posicion = Int32(indice + 1)
            switch valor {
            case .texto(let texto):
                sqlite3_bind_text(sentencia, posicion, texto, -1, Self.textoTransitorio)
            case .entero(let entero):
                sqlite3_bind_int64(sentencia, posicion, entero)
            case .nulo:
                sqlite3_bind_null(sentencia, posicion)
            }
        }
        return sentencia
    }

    enum ValorSql {
        case texto(String)
        case entero(Int64)
        case nulo

        init(_ texto: String?) {
            self = texto.map { .texto($0) } ?? .nulo
        }

        init(_ entero: Int64) {
            self = .entero(entero)
        }

        init(_ bandera: Bool) {
            self = .entero(bandera ? 1 : 0)
        }
    }

    struct Fila {
        let sentencia: OpaquePointer?

        func texto(_ columna: Int32) -> String? {
            guard let bytes = sqlite3_column_text(sentencia, columna) else {
                return nil
            }
            return String(cString: bytes)
        }

        func textoObligatorio(_ columna: Int32) -> String {
            texto(columna) ?? ""
        }

        func entero(_ columna: Int32) -> Int64 {
            sqlite3_column_int64(sentencia, columna)
        }

        func bandera(_ columna: Int32) -> Bool {
            entero(columna) != 0
        }
    }
}
