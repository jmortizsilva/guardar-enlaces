import CryptoKit
import Foundation

/// Entrar con un proveedor (Google o Apple), según el contrato del backend.
///
/// La app nunca habla con Google: abre `/auth/iniciar` del servidor en la
/// hoja de autenticación del sistema, el servidor hace todo el intercambio y
/// devuelve el navegador a `<esquema>://auth-callback?codigo=…`. Ese código
/// es de un solo uso y dura un minuto; cambiarlo por los tokens es cosa de la
/// sesión.
///
/// Aquí está lo único con casuística de verdad —leer la vuelta—, para poder
/// probarlo sin navegador.
public enum Login {
    public enum Resultado: Equatable, Sendable {
        case exito(codigoDeCanje: String)
        /// Cerró la hoja o rechazó el permiso. No es una avería, y la
        /// pantalla lo cuenta distinto.
        case cancelado
        case error(mensaje: String)
    }

    /// Cadena opaca de un solo uso que ata la vuelta del callback a esta
    /// petición concreta. 16 bytes en hexadecimal.
    public static func generarEstado(
        aleatorio: (Int) -> [UInt8] = { cuantos in
            (0..<cuantos).map { _ in UInt8.random(in: 0...255) }
        }
    ) -> String {
        aleatorio(16).map { String(format: "%02x", $0) }.joined()
    }

    /// Traduce los motivos de error del contrato a algo que se pueda leer en
    /// voz alta.
    public static func mensajeDeError(motivo: String) -> String {
        switch motivo {
        case "sin_email":
            "Tu cuenta no ha dado ningún correo, y hace falta para crear la cuenta."
        case "fallo_intercambio":
            "Google rechazó el inicio de sesión. Vuelve a intentarlo."
        default:
            "No se pudo iniciar sesión."
        }
    }

    /// Lee la URL de vuelta del callback.
    ///
    /// Se leen solo los parámetros, sin mirar el anfitrión ni la ruta: el
    /// esquema es propio de la app y lo único que importa es qué trae.
    public static func leerCallback(_ url: URL) -> Resultado {
        guard let partes = URLComponents(url: url, resolvingAgainstBaseURL: false),
            let parametros = partes.queryItems
        else {
            return .error(mensaje: mensajeDeError(motivo: ""))
        }
        let porNombre = Dictionary(
            parametros.map { ($0.name, $0.value ?? "") },
            uniquingKeysWith: { primero, _ in primero }
        )
        if let codigo = porNombre["codigo"], !codigo.isEmpty {
            return .exito(codigoDeCanje: codigo)
        }
        return .error(mensaje: mensajeDeError(motivo: porNombre["error"] ?? ""))
    }
}

extension Login {
    /// El par de valores que pide el inicio de sesión de Apple desde la app.
    ///
    /// A Apple se le manda solo el `resumen`, y al servidor el valor `enClaro`. El servidor
    /// comprueba que uno es el resumen del otro, y así sabe que el token se pidió para esta
    /// petición y no es uno de antes reutilizado.
    public struct NonceDeApple: Equatable, Sendable {
        public let enClaro: String
        public let resumen: String
    }

    public static func nonceParaApple(
        aleatorio: (Int) -> [UInt8] = { cuantos in
            (0..<cuantos).map { _ in UInt8.random(in: 0...255) }
        }
    ) -> NonceDeApple {
        let enClaro = aleatorio(32).map { String(format: "%02x", $0) }.joined()
        let resumen = SHA256.hash(data: Data(enClaro.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
        return NonceDeApple(enClaro: enClaro, resumen: resumen)
    }
}
