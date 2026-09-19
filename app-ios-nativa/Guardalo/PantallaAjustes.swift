import Dominio
import SwiftUI

struct PantallaAjustes: View {
    @Environment(ModeloApp.self) private var modelo
    @Binding var presentada: Bool
    @State private var entrando = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if modelo.autenticado {
                        Text(Textos.sesionIniciadaComo(email: modelo.usuario?.email ?? ""))
                        Button(Textos.cerrarSesion, role: .destructive) {
                            Task { await modelo.cerrarSesion() }
                        }
                        .accessibilityHint(Textos.pistaCerrarSesion)
                    } else {
                        Text(Textos.sinCuenta)
                        Button(Textos.entrarConGoogle) { entrando = true }
                            .accessibilityHint(Textos.pistaEntrar)
                        Button(Textos.entrarConApple) { Task { await entrarConApple() } }
                            .accessibilityHint(Textos.pistaEntrar)
                    }
                }

                Section {
                    Text(Self.versionInstalada)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle(Textos.ajustesTitulo)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(Textos.cerrar) { presentada = false }
                }
            }
            .sheet(isPresented: $entrando) {
                PantallaLogin(presentada: $entrando)
            }
        }
    }

    /// Desde Ajustes no hay enlaces de otra cuenta que decidir en la práctica,
    /// pero si los hubiera se pregunta igual que en la bienvenida.
    private func entrarConApple() async {
        let resultado = await modelo.iniciarSesionConApple(decidirImportacion: { _ in true })
        if case .exito = resultado {
            Anuncios.importante(Textos.sesionIniciada)
            presentada = false
        } else if case .error(let mensaje) = resultado {
            Anuncios.importante(mensaje)
        }
    }

    /// Para saber qué versión tienes instalada cuando llegan varias seguidas
    /// por TestFlight. Ocupa el sitio del antiguo «Buscar actualizaciones»,
    /// que sin actualizaciones por aire ya no busca nada.
    private static var versionInstalada: String {
        let info = Bundle.main.infoDictionary
        return Textos.version(
            info?["CFBundleShortVersionString"] as? String ?? "?",
            compilacion: info?["CFBundleVersion"] as? String ?? "?"
        )
    }
}

/// Entrar con una cuenta. No es un portón: aquí se llega desde Ajustes y la
/// app funciona entera sin pasar por aquí. Lo único que da la cuenta es
/// sincronizar con el ordenador, y por eso lo dice el texto en vez de pedir el
/// inicio de sesión a secas.
struct PantallaLogin: View {
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
                    Text(Textos.loginExplicacion)
                }

                if !error.isEmpty {
                    Section {
                        Text(error)
                            .foregroundStyle(.red)
                    }
                }

                Section {
                    Button(Textos.entrarConGoogle) { Task { await entrar() } }
                        .accessibilityHint(Textos.pistaLogin)
                        .disabled(entrando)
                    Button(Textos.ahoraNo) { presentada = false }
                        .disabled(entrando)
                }
            }
            .navigationTitle(Textos.loginTitulo)
            .navigationBarTitleDisplayMode(.inline)
            .alert(
                Textos.tituloEnlacesEnElTelefono,
                isPresented: .constant(enlacesQueDecidir != nil),
                presenting: enlacesQueDecidir
            ) { enlaces in
                // Sin botón de cancelar: hay que elegir, y las dos respuestas
                // son definitivas.
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

    private func entrar() async {
        entrando = true
        error = ""
        let resultado = await modelo.iniciarSesionConGoogle(decidirImportacion: preguntar)
        entrando = false

        switch resultado {
        case .exito:
            Anuncios.importante(Textos.sesionIniciada)
            presentada = false
        case .error(let mensaje):
            error = mensaje
            Anuncios.importante(mensaje)
        case .cancelado:
            // No se anuncia: lo ha hecho el usuario y, al cerrarse la hoja,
            // VoiceOver ya vuelve a leer esta pantalla.
            break
        }
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
