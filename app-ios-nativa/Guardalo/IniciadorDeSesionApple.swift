import AuthenticationServices
import Dominio
import SwiftUI

/// Pide la identidad a Apple sin salir de la app: el sistema pregunta y
/// resuelve con Face ID, huella o la contraseña del dispositivo.
///
/// A Apple se le manda solo el RESUMEN del nonce; el valor en claro viaja
/// aparte al servidor, que comprueba que uno es el resumen del otro. Así el
/// token no se puede reutilizar en otra petición.
@MainActor
final class IniciadorDeSesionApple: NSObject {
    enum Resultado {
        case exito(identityToken: String)
        /// Cerró la hoja del sistema. No es una avería.
        case cancelado
        case error(mensaje: String)
    }

    private var continuacion: CheckedContinuation<Resultado, Never>?

    func pedirIdentidad(resumenDelNonce: String) async -> Resultado {
        await withCheckedContinuation { continuacion in
            self.continuacion = continuacion

            let solicitud = ASAuthorizationAppleIDProvider().createRequest()
            // Solo el correo: el nombre no se usa en ningún sitio de la app, y lo
            // que no se necesita no se pide.
            solicitud.requestedScopes = [.email]
            solicitud.nonce = resumenDelNonce

            let controlador = ASAuthorizationController(authorizationRequests: [solicitud])
            controlador.delegate = self
            controlador.presentationContextProvider = self
            controlador.performRequests()
        }
    }

    private func responder(_ resultado: Resultado) {
        continuacion?.resume(returning: resultado)
        continuacion = nil
    }
}

extension IniciadorDeSesionApple: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        let credencial = authorization.credential as? ASAuthorizationAppleIDCredential
        let token = credencial?.identityToken.flatMap { String(data: $0, encoding: .utf8) }
        Task { @MainActor in
            guard let token else {
                responder(.error(mensaje: Login.mensajeDeError(motivo: "")))
                return
            }
            responder(.exito(identityToken: token))
        }
    }

    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: any Error
    ) {
        let cancelado = (error as? ASAuthorizationError)?.code == .canceled
        Task { @MainActor in
            responder(
                cancelado ? .cancelado : .error(mensaje: Login.mensajeDeError(motivo: ""))
            )
        }
    }
}

extension IniciadorDeSesionApple: ASAuthorizationControllerPresentationContextProviding {
    nonisolated func presentationAnchor(
        for controller: ASAuthorizationController
    ) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .first { $0.isKeyWindow } ?? ASPresentationAnchor()
        }
    }
}
