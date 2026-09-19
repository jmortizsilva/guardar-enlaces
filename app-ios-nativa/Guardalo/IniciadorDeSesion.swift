import AuthenticationServices
import Dominio
import SwiftUI

/// Abre el consentimiento del proveedor en la hoja de autenticación del
/// sistema y espera la vuelta.
///
/// `ASWebAuthenticationSession` captura la redirección final ella misma, así
/// que la vuelta NO pasa por el manejador de enlaces de la app y no hace
/// falta registrar el esquema en el Info.plist.
///
/// Sin sesión efímera a propósito: así comparte las cookies de Safari y, si
/// ya hay sesión de Google en el teléfono, basta con confirmar la cuenta en
/// vez de teclear correo y contraseña.
@MainActor
final class IniciadorDeSesion: NSObject, ASWebAuthenticationPresentationContextProviding {
    /// Propio, y distinto del `guardarenlaces` que usa la app de Expo: con el
    /// mismo esquema en las dos apps instaladas a la vez, iOS entregaría la
    /// vuelta del inicio de sesión a cualquiera de ellas. Vuelve a ser el de
    /// siempre en la fase 5, cuando esta sustituya a aquella.
    static let esquema = "guardarenlaces"

    func pedirCodigoDeCanje(urlAutorizacion: URL) async -> Login.Resultado {
        await withCheckedContinuation { continuacion in
            let hoja = ASWebAuthenticationSession(
                url: urlAutorizacion,
                callbackURLScheme: Self.esquema
            ) { urlDeVuelta, _ in
                guard let urlDeVuelta else {
                    // Cerró la hoja o rechazó el permiso: no es una avería.
                    continuacion.resume(returning: .cancelado)
                    return
                }
                continuacion.resume(returning: Login.leerCallback(urlDeVuelta))
            }
            hoja.presentationContextProvider = self
            if !hoja.start() {
                continuacion.resume(
                    returning: .error(mensaje: Login.mensajeDeError(motivo: ""))
                )
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}
