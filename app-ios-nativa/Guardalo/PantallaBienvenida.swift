import AuthenticationServices
import Dominio
import Fontaneria
import SwiftUI

/// Lo primero que se ve al estrenar la app: qué es, y qué cambia según se
/// entre con cuenta o no.
///
/// Las tres opciones están al mismo nivel a propósito: usar la app sin cuenta
/// no es una salida de emergencia, es una forma de usarla.
struct PantallaBienvenida: View {
    @Environment(ModeloApp.self) private var modelo
    @Binding var presentada: Bool

    @State private var entrando = false
    @State private var error = ""
    @State private var enlacesQueDecidir: EnlacesEnElTelefono?
    @State private var decision: CheckedContinuation<Bool, Never>?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(Textos.bienvenidaQueEs)
                    Text(Textos.bienvenidaCuenta)
                }

                if !error.isEmpty {
                    Section {
                        Text(error).foregroundStyle(.red)
                    }
                }

                Section {
                    Button(Textos.entrarConApple) { Task { await entrar(conApple: true) } }
                        .disabled(entrando)
                    Button(Textos.entrarConGoogle) { Task { await entrar(conApple: false) } }
                        .accessibilityHint(Textos.pistaLogin)
                        .disabled(entrando)
                    Button(Textos.usarSinCuenta) { cerrar() }
                        .disabled(entrando)
                } footer: {
                    Text(Textos.bienvenidaMasTarde)
                }
            }
            .navigationTitle(Textos.bienvenidaTitulo)
            .interactiveDismissDisabled()
            .alert(
                Textos.tituloEnlacesEnElTelefono,
                isPresented: .constant(enlacesQueDecidir != nil),
                presenting: enlacesQueDecidir
            ) { _ in
                Button(Textos.anadirlos) { responder(true) }
                Button(Textos.borrarlos, role: .destructive) { responder(false) }
            } message: { enlaces in
                Text(
                    Textos.preguntaImportar(
                        cuantos: enlaces.cuantos,
                        deOtraCuenta: enlaces.deOtraCuenta
                    )
                )
            }
        }
    }

    private func entrar(conApple: Bool) async {
        entrando = true
        error = ""
        let resultado =
            conApple
            ? await modelo.iniciarSesionConApple(decidirImportacion: preguntar)
            : await modelo.iniciarSesionConGoogle(decidirImportacion: preguntar)
        entrando = false

        switch resultado {
        case .exito:
            Anuncios.importante(Textos.sesionIniciada)
            cerrar()
        case .error(let mensaje):
            error = mensaje
            Anuncios.importante(mensaje)
        case .cancelado:
            break
        }
    }

    /// Se recuerda que ya se vio, para no volver a enseñarla en cada arranque.
    private func cerrar() {
        UserDefaults.standard.set(true, forKey: ModeloApp.claveBienvenidaVista)
        presentada = false
    }

    private func preguntar(_ enlaces: EnlacesEnElTelefono) async -> Bool {
        await withCheckedContinuation { continuacion in
            enlacesQueDecidir = enlaces
            decision = continuacion
        }
    }

    private func responder(_ importar: Bool) {
        enlacesQueDecidir = nil
        decision?.resume(returning: importar)
        decision = nil
    }
}
