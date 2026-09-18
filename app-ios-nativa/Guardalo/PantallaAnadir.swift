import Dominio
import SwiftUI

/// Pegar una URL y guardarla.
///
/// La comprobación va sola en segundo plano y rellena la vista previa, pero
/// nunca hace falta esperarla: guardar funciona en cuanto la dirección es
/// válida. Si la comprobación no llega, el enlace se guarda sin título y se
/// dice. Es lo mismo que hace la app de Windows, y por el mismo motivo:
/// comprobar no es lo importante, guardar el enlace sí.
struct PantallaAnadir: View {
    @Environment(ModeloApp.self) private var modelo
    /// Se cierra con un enlace al estado del padre y no con `dismiss` del
    /// entorno. Con `dismiss`, después de abrir y cerrar la hoja de
    /// etiquetas (una hoja dentro de otra), pulsar Guardar dejaba de cerrar
    /// esta pantalla: el `dismiss` heredado ya no apunta aquí. Comprobado con
    /// la prueba de interfaz, que se quedaba esperando en la hoja abierta.
    @Binding var presentada: Bool

    @State private var url = ""
    @State private var etiquetas: [String] = []
    @State private var vistaPrevia: MetadatosExtraidos?
    @State private var comprobando = false
    @State private var etiquetasAbiertas = false
    @State private var comprobacion: Task<MetadatosExtraidos?, Never>?
    @FocusState private var enElCampo: Bool

    private var urlLimpia: String {
        url.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var urlValida: Bool {
        urlLimpia.hasPrefix("http://") || urlLimpia.hasPrefix("https://")
    }

    private var repetido: Elemento? {
        urlValida ? modelo.repetido(para: urlLimpia) : nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(Textos.marcadorUrl, text: $url)
                        .accessibilityLabel(Textos.campoUrl)
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .focused($enElCampo)
                } footer: {
                    if !urlLimpia.isEmpty && !urlValida {
                        Text(Textos.urlNoValida)
                    }
                }

                Section {
                    Button(Textos.botonEtiquetas(etiquetas)) {
                        etiquetasAbiertas = true
                    }
                }

                if comprobando {
                    Section {
                        Text(Textos.comprobando)
                    }
                }

                if let vistaPrevia, let titulo = vistaPrevia.titulo {
                    Section {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(titulo).font(.headline)
                            if let descripcion = vistaPrevia.descripcion {
                                Text(descripcion)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                }

                if repetido != nil {
                    Section {
                        Text(Textos.enlaceRepetido)
                    }
                }
            }
            .navigationTitle(Textos.anadirEnlaceTitulo)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(Textos.cancelar) { presentada = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(repetido == nil ? Textos.guardar : Textos.actualizar) {
                        Task { await guardar() }
                    }
                    .disabled(!urlValida)
                }
            }
            .sheet(isPresented: $etiquetasAbiertas) {
                PantallaEtiquetas(
                    disponibles: modelo.etiquetasDisponibles,
                    elegidas: etiquetas
                ) { elegidas in
                    etiquetas = elegidas
                }
            }
            .onChange(of: url) { _, _ in alCambiarLaUrl() }
            .onChange(of: repetido?.id) { _, nuevo in
                // Se avisa en cuanto se detecta. Enterarte de que estaba
                // repetido cuando ya lo has guardado no sirve de nada.
                if nuevo != nil {
                    Anuncios.importante(Textos.enlaceRepetido)
                }
            }
            .onAppear { enElCampo = true }
        }
    }

    /// Cambiar la dirección invalida lo comprobado: lo de antes era de otra
    /// página. Se vuelve a comprobar, con un respiro para no lanzar una
    /// petición por cada tecla al escribir a mano.
    private func alCambiarLaUrl() {
        comprobacion?.cancel()
        vistaPrevia = nil
        guard urlValida else {
            comprobando = false
            return
        }
        let direccion = urlLimpia
        comprobando = true
        comprobacion = Task {
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return nil }
            let metadatos = await modelo.comprobar(url: direccion)
            guard !Task.isCancelled else { return nil }
            vistaPrevia = metadatos
            comprobando = false
            return metadatos
        }
    }

    private func guardar() async {
        guard urlValida else { return }
        modelo.guardarEnlace(
            url: urlLimpia,
            etiquetas: etiquetas,
            metadatos: await metadatosParaGuardar()
        )
        presentada = false
    }

    /// Lo que se sepa de la página en este momento.
    ///
    /// Si la comprobación ya había terminado, se usa. Si sigue en marcha, se
    /// le da un respiro corto y nada más: un sitio que no contesta tarda diez
    /// segundos en rendirse, y dejar la pantalla clavada todo ese rato sin
    /// decir nada es justo lo que no puede pasar con lector de pantalla.
    /// Lo que se pierde es el título, y eso se anuncia.
    private func metadatosParaGuardar() async -> MetadatosExtraidos? {
        if let yaComprobado = vistaPrevia {
            return yaComprobado
        }
        guard let enMarcha = comprobacion else {
            return nil
        }
        return await withTaskGroup(of: MetadatosExtraidos?.self) { grupo in
            grupo.addTask { await enMarcha.value }
            grupo.addTask {
                try? await Task.sleep(for: .seconds(2))
                return nil
            }
            let primero = await grupo.next() ?? nil
            grupo.cancelAll()
            return primero
        }
    }
}
