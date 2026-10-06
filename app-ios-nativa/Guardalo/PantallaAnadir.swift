import Dominio
import Fontaneria
import SwiftUI

/// Pegar o escribir una URL y guardarla. No hace falta escribir `https://`:
/// si falta, se pone.
///
/// La comprobación va sola en segundo plano mientras se escribe y rellena la
/// vista previa. Además dice si la página carga: si no, antes de guardar se
/// pregunta. Sin red no se puede saber, y se guarda sin preguntar. Las reglas
/// están en `ANADIR.md`, iguales para las tres apps.
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
    @State private var comprobacion: Task<DireccionComprobada?, Never>?
    /// La que no ha cargado, mientras se pregunta si guardarla igualmente.
    @State private var noCarga: DireccionComprobada?
    /// Al cancelar la pregunta, el cursor vuelve al campo, que es donde está
    /// lo que haya que corregir.
    @AccessibilityFocusState private var cursorEnElCampo: Bool
    /// Desde que se pulsa Guardar hasta que la pantalla se cierra.
    ///
    /// Sin esto, guardar un enlace NUEVO avisaba de que ya lo tenías: el
    /// enlace entra en la lista, la pantalla todavía no se ha cerrado, y al
    /// recomponerse busca repetidos y se encuentra a sí mismo. El aviso
    /// llegaba justo cuando ya no era verdad.
    @State private var guardando = false
    @FocusState private var enElCampo: Bool
    /// Si el portapapeles trae una dirección. Se consulta sin leerlo: saber
    /// que hay una URL no cuenta como mirar, y así no sale el aviso de iOS.
    @State private var hayEnlaceCopiado = false

    private var urlLimpia: String {
        url.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var escrita: DireccionEscrita? {
        Enlaces.completar(urlLimpia)
    }

    private var urlValida: Bool {
        escrita != nil
    }

    private var repetido: Elemento? {
        guard let escrita, !guardando else {
            return nil
        }
        return modelo.repetido(para: escrita.direccion)
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
                        .accessibilityFocused($cursorEnElCampo)
                    // El botón de pegar del sistema, y no leer el
                    // portapapeles por nuestra cuenta: leerlo directamente
                    // saca el aviso de «Guárdalo ha pegado de…» cada vez, y
                    // con lector de pantalla eso es una frase de más en cada
                    // enlace que guardas. Así lo pides tú y no avisa nadie.
                    if hayEnlaceCopiado && urlLimpia.isEmpty {
                        PasteButton(payloadType: URL.self) { direcciones in
                            guard let primera = direcciones.first else { return }
                            url = primera.absoluteString
                        }
                        .labelStyle(.titleOnly)
                    }
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
            .alert(
                Textos.tituloNoCarga,
                isPresented: Binding(
                    get: { noCarga != nil },
                    set: { if !$0 { noCarga = nil } }
                ),
                presenting: noCarga
            ) { comprobada in
                Button(Textos.guardarIgualmente) {
                    noCarga = nil
                    guardando = true
                    modelo.guardarEnlace(
                        url: comprobada.direccion,
                        etiquetas: etiquetas,
                        metadatos: nil
                    )
                    presentada = false
                }
                Button(Textos.cancelar, role: .cancel) {
                    noCarga = nil
                    Task {
                        try? await Task.sleep(for: .milliseconds(300))
                        cursorEnElCampo = true
                    }
                }
            } message: { _ in
                Text(Textos.noCarga(urlLimpia))
            }
            .onChange(of: url) { _, _ in alCambiarLaUrl() }
            .onChange(of: repetido?.id) { _, nuevo in
                // Se avisa en cuanto se detecta. Enterarte de que estaba
                // repetido cuando ya lo has guardado no sirve de nada.
                if nuevo != nil {
                    Anuncios.importante(Textos.enlaceRepetido)
                }
            }
            .onAppear {
                enElCampo = true
                hayEnlaceCopiado = UIPasteboard.general.hasURLs
                if hayEnlaceCopiado {
                    // Con un respiro y en prioridad alta. Lanzado justo al
                    // aparecer, el aviso se perdía: VoiceOver estaba leyendo
                    // la pantalla recién abierta y uno que espera turno nunca
                    // llegaba a sonar.
                    Task {
                        try? await Task.sleep(for: .milliseconds(900))
                        Anuncios.importante(Textos.hayEnlaceCopiado)
                    }
                }
            }
        }
    }

    /// Cambiar la dirección invalida lo comprobado: lo de antes era de otra
    /// página. Se vuelve a comprobar, con un respiro para no lanzar una
    /// petición por cada tecla al escribir a mano.
    private func alCambiarLaUrl() {
        comprobacion?.cancel()
        vistaPrevia = nil
        guard let escrita else {
            comprobando = false
            return
        }
        comprobando = true
        comprobacion = Task {
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return nil }
            let resultado = await ComprobarDireccion.comprobar(escrita) {
                await modelo.comprobar(url: $0)
            }
            guard !Task.isCancelled else { return nil }
            vistaPrevia = resultado.comprobacion.metadatos
            comprobando = false
            return resultado
        }
    }

    private func guardar() async {
        guard let escrita, !guardando else { return }
        guardando = true
        let resultado = await loQueSeSabe()
        switch resultado?.comprobacion {
        case .noCarga:
            guardando = false
            noCarga = resultado
        case .carga(let metadatos):
            modelo.guardarEnlace(
                url: resultado?.direccion ?? escrita.direccion,
                etiquetas: etiquetas,
                metadatos: metadatos
            )
            presentada = false
        case .sinComprobar, nil:
            modelo.guardarEnlace(url: escrita.direccion, etiquetas: etiquetas, metadatos: nil)
            presentada = false
        }
    }

    /// Lo que se sepa de la página. Para saber si carga hay que esperar a la
    /// comprobación: si sigue en marcha se dice, porque una pantalla clavada
    /// sin decir nada es justo lo que no puede pasar con lector de pantalla,
    /// y se espera con un límite. Pasado, se guarda como si no hubiera red.
    /// Antes eran dos segundos, cuando la comprobación solo daba el título.
    private func loQueSeSabe() async -> DireccionComprobada? {
        guard let enMarcha = comprobacion else {
            return nil
        }
        if comprobando {
            Anuncios.informativo(Textos.comprobando)
        }
        return await withTaskGroup(of: DireccionComprobada?.self) { grupo in
            grupo.addTask { await enMarcha.value }
            grupo.addTask {
                try? await Task.sleep(for: .seconds(12))
                return nil
            }
            let primero = await grupo.next() ?? nil
            grupo.cancelAll()
            return primero
        }
    }
}
