import Foundation

@testable import Fontaneria

/// Llavero de mentira. El de verdad (`CredencialesKeychain`) no se puede
/// ejercitar con `swift test` en el Mac: necesita la autorización de llavero
/// de la app firmada, así que ese se comprueba en el simulador.
final class CredencialesEnMemoria: AlmacenCredenciales, @unchecked Sendable {
    private let cerrojo = NSLock()
    private var token: String?
    private(set) var escrituras = 0

    init(tokenInicial: String? = nil) {
        token = tokenInicial
    }

    func guardarTokenRefresco(_ nuevo: String) throws {
        cerrojo.withLock {
            token = nuevo
            escrituras += 1
        }
    }

    func tokenRefresco() throws -> String? {
        cerrojo.withLock { token }
    }

    func borrarTokenRefresco() throws {
        cerrojo.withLock { token = nil }
    }
}
