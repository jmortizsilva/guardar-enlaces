import Dominio
import Fontaneria
import SwiftUI

@main
struct GuardaloApp: App {
    var body: some Scene {
        WindowGroup {
            PantallaAndamiaje()
        }
    }
}

/// Provisional: comprueba que la app arranca, que enlaza con los dos
/// paquetes y que SQLite funciona de verdad en el teléfono, que es lo único
/// que no se puede comprobar con `swift test` en el Mac. La sustituye la
/// lista de enlaces en la fase 3.
struct PantallaAndamiaje: View {
    @State private var estado = "Abriendo la base de datos…"

    var body: some View {
        VStack(spacing: 16) {
            Text("Guárdalo")
                .font(.largeTitle.bold())
                .accessibilityAddTraits(.isHeader)
            Text(estado)
                .multilineTextAlignment(.center)
        }
        .padding()
        .task { estado = Self.comprobarElAlmacen() }
    }

    private static func comprobarElAlmacen() -> String {
        do {
            let almacen = try AlmacenLocal(ruta: try AlmacenLocal.rutaPorDefecto())
            let guardados = try almacen.contarElementos()
            let etiquetas = try almacen.cargarEtiquetasDefinidas().count
            return "Andamiaje: \(guardados) enlaces y \(etiquetas) etiquetas en la base de datos."
        } catch {
            return "La base de datos no abrió: \(error)"
        }
    }
}
