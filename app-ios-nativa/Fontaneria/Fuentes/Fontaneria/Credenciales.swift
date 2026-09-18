import Foundation
import Security

/// Dónde se guarda el token de refresco. Se puede sustituir en las pruebas,
/// que es lo que permite probar `Sesion` entera sin llavero.
public protocol AlmacenCredenciales: Sendable {
    func guardarTokenRefresco(_ token: String) throws
    func tokenRefresco() throws -> String?
    func borrarTokenRefresco() throws
}

/// El token de refresco en el llavero de iOS, nunca en un fichero ni en
/// preferencias. El token de ACCESO no se guarda: dura poco y vive solo en
/// memoria mientras la app está abierta.
public struct CredencialesKeychain: AlmacenCredenciales {
    private let servicio: String
    private let cuenta: String

    public init(servicio: String = "guardalo", cuenta: String = "token-refresco") {
        self.servicio = servicio
        self.cuenta = cuenta
    }

    public struct Fallo: Error, CustomStringConvertible {
        public let estado: OSStatus
        public var description: String {
            "el llavero devolvió el error \(estado)"
        }
    }

    private func consultaBase() -> [String: Any] {
        // Sin grupo de llavero explícito: al ser el único grupo propio de la
        // app y de la extensión de compartir (misma autorización en los dos
        // objetivos), iOS lo usa por defecto en ambos sitios sin tener que
        // conocer el identificador del equipo en ningún lado.
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servicio,
            kSecAttrAccount as String: cuenta,
        ]
    }

    public func guardarTokenRefresco(_ token: String) throws {
        let datos = Data(token.utf8)
        // Solo con el teléfono desbloqueado: compartir un enlace exige
        // pantalla desbloqueada, así que la extensión llega igual, y el token
        // no queda accesible con el dispositivo bloqueado.
        let atributos: [String: Any] = [
            kSecValueData as String: datos,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlocked,
        ]

        let actualizacion = SecItemUpdate(
            consultaBase() as CFDictionary,
            atributos as CFDictionary
        )
        if actualizacion == errSecSuccess {
            return
        }
        guard actualizacion == errSecItemNotFound else {
            throw Fallo(estado: actualizacion)
        }

        var nuevo = consultaBase()
        nuevo.merge(atributos) { _, nuevoValor in nuevoValor }
        let alta = SecItemAdd(nuevo as CFDictionary, nil)
        guard alta == errSecSuccess else {
            throw Fallo(estado: alta)
        }
    }

    public func tokenRefresco() throws -> String? {
        var consulta = consultaBase()
        consulta[kSecMatchLimit as String] = kSecMatchLimitOne
        consulta[kSecReturnData as String] = true

        var resultado: CFTypeRef?
        let estado = SecItemCopyMatching(consulta as CFDictionary, &resultado)
        if estado == errSecItemNotFound {
            return nil
        }
        guard estado == errSecSuccess, let datos = resultado as? Data else {
            throw Fallo(estado: estado)
        }
        return String(data: datos, encoding: .utf8)
    }

    public func borrarTokenRefresco() throws {
        let estado = SecItemDelete(consultaBase() as CFDictionary)
        // Cerrar sesión sin haberla abierto no es un error.
        guard estado == errSecSuccess || estado == errSecItemNotFound else {
            throw Fallo(estado: estado)
        }
    }
}
