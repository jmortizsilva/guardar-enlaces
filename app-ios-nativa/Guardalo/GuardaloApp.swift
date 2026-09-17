import Dominio
import SwiftUI

@main
struct GuardaloApp: App {
    var body: some Scene {
        WindowGroup {
            PantallaAndamiaje()
        }
    }
}

/// Provisional: solo comprueba que la app arranca y que el paquete `Dominio`
/// está enlazado de verdad. La sustituye la lista de enlaces en la fase 3.
struct PantallaAndamiaje: View {
    var body: some View {
        VStack(spacing: 16) {
            Text("Guárdalo")
                .font(.largeTitle.bold())
                .accessibilityAddTraits(.isHeader)
            Text("Andamiaje: el dominio responde \(relojDelSistema()).")
        }
        .padding()
    }
}
