import Dominio
import Foundation

/// Lo que responde el servidor al canjear un código o al renovar la sesión.
public struct RespuestaCanje: Decodable, Sendable {
    public let tokenAcceso: String
    public let expiraEn: MarcaDeTiempo
    public let tokenRefresco: String
    public let usuario: UsuarioApi?
}

public struct UsuarioApi: Decodable, Sendable, Equatable {
    public let id: Int
    public let email: String
    public let proveedor: String
}

public struct RespuestaMetadatos: Decodable, Sendable, Equatable {
    public let titulo: String?
    public let descripcion: String?
    public let imagenUrl: String?
    public let tipo: String

    public var comoMetadatos: MetadatosExtraidos {
        MetadatosExtraidos(
            titulo: titulo,
            descripcion: descripcion,
            imagenUrl: imagenUrl,
            tipo: TipoElemento(rawValue: tipo) ?? .enlace
        )
    }
}

/// Una entrada que el servidor no ha aplicado. Hay que sacarla de la cola
/// igualmente: no se va a aceptar por mucho que se insista, y dejarla dentro
/// reenvía el lote entero en cada sincronización, para siempre.
public struct EntradaRechazada: Decodable, Sendable, Equatable {
    public let id: String
    /// `sin_url`, `sin_nombre` o `no_aplicable` (ver CONTRATO-API.md).
    public let motivo: String
}

public struct RespuestaPush: Decodable, Sendable {
    enum CodingKeys: String, CodingKey {
        case elementos, rechazados, etiquetasDefinidas, etiquetasRechazadas
    }

    public let elementos: [Elemento]
    public let rechazados: [EntradaRechazada]
    public let etiquetasDefinidas: [EtiquetaDefinida]
    public let etiquetasRechazadas: [EntradaRechazada]

    public init(from decoder: any Decoder) throws {
        let campos = try decoder.container(keyedBy: CodingKeys.self)
        elementos = try campos.decodeIfPresent([Elemento].self, forKey: .elementos) ?? []
        rechazados = try campos.decodeIfPresent([EntradaRechazada].self, forKey: .rechazados) ?? []
        etiquetasDefinidas =
            try campos.decodeIfPresent([EtiquetaDefinida].self, forKey: .etiquetasDefinidas) ?? []
        etiquetasRechazadas =
            try campos.decodeIfPresent([EntradaRechazada].self, forKey: .etiquetasRechazadas) ?? []
    }
}

public struct RespuestaSincronizar: Decodable, Sendable {
    enum CodingKeys: String, CodingKey {
        case elementos, etiquetasDefinidas, servidorEn, masDisponible
    }

    public let elementos: [Elemento]
    public let etiquetasDefinidas: [EtiquetaDefinida]
    public let servidorEn: MarcaDeTiempo
    public let masDisponible: Bool

    public init(from decoder: any Decoder) throws {
        let campos = try decoder.container(keyedBy: CodingKeys.self)
        elementos = try campos.decodeIfPresent([Elemento].self, forKey: .elementos) ?? []
        etiquetasDefinidas =
            try campos.decodeIfPresent([EtiquetaDefinida].self, forKey: .etiquetasDefinidas) ?? []
        servidorEn = try campos.decodeIfPresent(MarcaDeTiempo.self, forKey: .servidorEn) ?? 0
        masDisponible = try campos.decodeIfPresent(Bool.self, forKey: .masDisponible) ?? false
    }
}

/// Un fallo hablando con el servidor, ya traducido a algo que se puede leer
/// en voz alta. `codigo` es el estado HTTP cuando lo hay; sin él, es que no
/// se llegó a hablar con nadie.
public struct ErrorApi: Error, CustomStringConvertible, Sendable {
    public let mensaje: String
    public let codigo: Int?

    public var description: String { mensaje }

    /// El token de acceso ha caducado y toca renovarlo.
    public var esSesionCaducada: Bool { codigo == 401 }
}

/// Cliente HTTP del backend. El contrato completo está en
/// `backend/docs/CONTRATO-API.md`.
///
/// No decide nada: traduce llamadas a peticiones y respuestas a tipos. Quién
/// reintenta y cuándo es cosa de `Sesion`.
public struct ClienteApi: Sendable {
    private let urlBase: String
    private let sesionHttp: URLSession

    public init(urlBase: String, sesionHttp: URLSession = .sesionPorDefecto) {
        self.urlBase = urlBase.replacingOccurrences(
            of: "/+$",
            with: "",
            options: .regularExpression
        )
        self.sesionHttp = sesionHttp
    }

    // MARK: - Autenticación

    /// Dirección con la que arranca el login. No se pide desde aquí: se abre
    /// en la hoja de autenticación del sistema, y el servidor responde con
    /// una redirección al consentimiento del proveedor.
    public func urlIniciarLogin(proveedor: String, estado: String, esquema: String) -> URL? {
        var partes = URLComponents(string: "\(urlBase)/auth/iniciar")
        partes?.queryItems = [
            URLQueryItem(name: "proveedor", value: proveedor),
            URLQueryItem(name: "modo", value: "deeplink"),
            URLQueryItem(name: "estado", value: estado),
            URLQueryItem(name: "esquema", value: esquema),
        ]
        return partes?.url
    }

    /// Cambia el código de canje (un solo uso, unos 60 segundos de vida) por
    /// los tokens de sesión.
    public func canjear(codigoCanje: String) async throws -> RespuestaCanje {
        try await pedir("/auth/canjear", cuerpo: ["codigoCanje": codigoCanje])
    }

    /// Solo sirve contra un servidor con `PERMITIR_LOGIN_DEV=true`. Queda
    /// como la única forma de entrar en el backend de pruebas local, que
    /// arranca con credenciales de Google falsas.
    public func loginDeDesarrollo(email: String) async throws -> RespuestaCanje {
        try await pedir("/auth/dev-login", cuerpo: ["email": email])
    }

    public func renovar(tokenRefresco: String) async throws -> RespuestaCanje {
        try await pedir("/auth/renovar", cuerpo: ["tokenRefresco": tokenRefresco])
    }

    public func cerrarSesion(tokenRefresco: String) async throws {
        let _: RespuestaVacia = try await pedir(
            "/auth/logout",
            cuerpo: ["tokenRefresco": tokenRefresco]
        )
    }

    // MARK: - Metadatos

    public func metadatos(url: String, tokenAcceso: String) async throws -> RespuestaMetadatos {
        try await pedir("/metadatos", cuerpo: ["url": url], tokenAcceso: tokenAcceso)
    }

    // MARK: - Sincronización

    public func bajarCambios(
        desde: MarcaDeTiempo,
        tokenAcceso: String,
        limite: Int = 300
    ) async throws -> RespuestaSincronizar {
        var partes = URLComponents(string: "\(urlBase)/sincronizar")
        partes?.queryItems = [
            URLQueryItem(name: "desde", value: String(desde)),
            URLQueryItem(name: "limite", value: String(limite)),
        ]
        guard let url = partes?.url else {
            throw ErrorApi(mensaje: "la dirección del servidor no es válida", codigo: nil)
        }
        var peticion = URLRequest(url: url)
        peticion.httpMethod = "GET"
        peticion.setValue("Bearer \(tokenAcceso)", forHTTPHeaderField: "Authorization")
        return try await enviar(peticion)
    }

    public func subirCambios(
        elementos: [Elemento],
        etiquetas: [EtiquetaDefinida],
        tokenAcceso: String
    ) async throws -> RespuestaPush {
        struct CuerpoPush: Encodable {
            let elementos: [Elemento]?
            let etiquetasDefinidas: [EtiquetaDefinida]?
        }
        // Los dos son opcionales, pero hace falta al menos uno: se manda solo
        // lo que hay, para no enviar listas vacías que el servidor rechaza.
        let cuerpo = CuerpoPush(
            elementos: elementos.isEmpty ? nil : elementos,
            etiquetasDefinidas: etiquetas.isEmpty ? nil : etiquetas
        )
        return try await pedir("/sincronizar", cuerpoCodificable: cuerpo, tokenAcceso: tokenAcceso)
    }

    // MARK: - Fontanería interna

    private struct RespuestaVacia: Decodable {}

    private func pedir<T: Decodable>(
        _ ruta: String,
        cuerpo: [String: String],
        tokenAcceso: String? = nil
    ) async throws -> T {
        try await pedir(ruta, cuerpoCodificable: cuerpo, tokenAcceso: tokenAcceso)
    }

    private func pedir<T: Decodable, C: Encodable>(
        _ ruta: String,
        cuerpoCodificable: C,
        tokenAcceso: String? = nil
    ) async throws -> T {
        guard let url = URL(string: "\(urlBase)\(ruta)") else {
            throw ErrorApi(mensaje: "la dirección del servidor no es válida", codigo: nil)
        }
        var peticion = URLRequest(url: url)
        peticion.httpMethod = "POST"
        peticion.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let tokenAcceso {
            peticion.setValue("Bearer \(tokenAcceso)", forHTTPHeaderField: "Authorization")
        }
        peticion.httpBody = try JSONEncoder().encode(cuerpoCodificable)
        return try await enviar(peticion)
    }

    private func enviar<T: Decodable>(_ peticion: URLRequest) async throws -> T {
        let datos: Data
        let respuesta: URLResponse
        do {
            (datos, respuesta) = try await sesionHttp.data(for: peticion)
        } catch {
            // Los mensajes de URLSession llegan en inglés y contando cosas de
            // red que aquí no ayudan. Esta frase acaba leída en voz alta.
            // En minúscula y sin verbo: esto se lee solo ("Sin conexión con
            // el servidor") y también como causa detrás de la acción que
            // falló ("No se pudo sincronizar: sin conexión con el servidor").
            throw ErrorApi(mensaje: "sin conexión con el servidor", codigo: nil)
        }

        let codigo = (respuesta as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(codigo) else {
            throw ErrorApi(mensaje: Self.mensajeDeError(datos, codigo: codigo), codigo: codigo)
        }
        if datos.isEmpty, let vacia = RespuestaVacia() as? T {
            return vacia
        }
        do {
            return try JSONDecoder().decode(T.self, from: datos)
        } catch {
            throw ErrorApi(mensaje: "el servidor respondió algo que no se entiende", codigo: codigo)
        }
    }

    /// El cuerpo de error del contrato es `{"error": "..."}`.
    private static func mensajeDeError(_ datos: Data, codigo: Int) -> String {
        struct CuerpoError: Decodable {
            let error: String?
        }
        if let cuerpo = try? JSONDecoder().decode(CuerpoError.self, from: datos),
            let detalle = cuerpo.error,
            !detalle.isEmpty
        {
            return detalle
        }
        return "el servidor respondió con un error \(codigo)"
    }
}

extension URLSession {
    /// Diez segundos: lo que esperaba la app de Expo. Más que eso, en un
    /// móvil, es tiempo mirando una pantalla que no dice nada.
    public static let sesionPorDefecto: URLSession = {
        let configuracion = URLSessionConfiguration.default
        configuracion.timeoutIntervalForRequest = 10
        return URLSession(configuration: configuracion)
    }()
}
