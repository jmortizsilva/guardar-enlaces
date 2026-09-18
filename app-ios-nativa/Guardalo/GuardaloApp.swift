import Dominio
import SwiftUI

@main
struct GuardaloApp: App {
    @State private var modelo: ModeloApp?
    @State private var fallo: String?
    @Environment(\.scenePhase) private var fase

    var body: some Scene {
        WindowGroup {
            if let modelo {
                PantallaLista()
                    .environment(modelo)
                    .task { await modelo.arrancar() }
                    .onChange(of: fase) { _, nueva in
                        if nueva == .active {
                            Task { await modelo.alVolverAPrimerPlano() }
                        }
                    }
            } else if let fallo {
                ContentUnavailableView(
                    "No se pudo abrir la aplicación",
                    systemImage: "exclamationmark.triangle",
                    description: Text(fallo)
                )
            } else {
                ProgressView()
                    .task { arrancar() }
            }
        }
    }

    private func arrancar() {
        do {
            modelo = try ModeloApp()
        } catch {
            // Si la base de datos no abre no hay nada que hacer, pero sí hay
            // que decirlo: una pantalla en blanco no cuenta qué ha pasado.
            fallo = "\(error)"
        }
    }
}
