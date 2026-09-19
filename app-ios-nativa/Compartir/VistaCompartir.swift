import Dominio
import SwiftUI

/// La hoja que sale al compartir un enlace desde otra aplicación.
///
/// Es la misma decisión que la pantalla de añadir, recortada a lo que hace
/// falta aquí: la dirección ya viene dada y no se puede escribir, así que
/// solo quedan las etiquetas y los dos botones.
struct VistaCompartir: View {
    @State var modelo: ModeloCompartir
    let alGuardar: () -> Void
    let alCancelar: () -> Void

    @State private var nueva = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(modelo.metadatos?.titulo ?? Textos.compartirSinTitulo)
                            .font(.headline)
                        Text(modelo.url)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                    // Título y dirección son una sola cosa que leer, no dos
                    // paradas del dedo.
                    .accessibilityElement(children: .combine)

                    if modelo.comprobando {
                        Text(Textos.comprobando)
                            .foregroundStyle(.secondary)
                    }
                }

                Section(Textos.compartirEtiquetas) {
                    if modelo.etiquetasDisponibles.isEmpty {
                        Text(Textos.compartirSinEtiquetas)
                            .foregroundStyle(.secondary)
                    }
                    ForEach(modelo.etiquetasDisponibles, id: \.self) { etiqueta in
                        Button {
                            alternar(etiqueta)
                        } label: {
                            HStack {
                                Text(etiqueta)
                                Spacer()
                                if modelo.etiquetasPuestas.contains(etiqueta) {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                        .tint(.primary)
                        // Lo que VoiceOver tiene que decir de una etiqueta
                        // es si está puesta, y eso es un interruptor, no
                        // un botón con un dibujo dentro.
                        .accessibilityRemoveTraits(.isButton)
                        .accessibilityAddTraits(
                            modelo.etiquetasPuestas.contains(etiqueta)
                                ? [.isToggle, .isSelected] : .isToggle
                        )
                    }

                    // La etiqueta que falta se echa de menos justo al
                    // guardar, igual que en la pantalla de añadir, así que
                    // aquí se crea igual que allí.
                    HStack {
                        TextField(Textos.nuevaEtiqueta, text: $nueva)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .onSubmit(crear)
                        Button(Textos.anadir, action: crear)
                            .disabled(nueva.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .navigationTitle(Textos.compartirTitulo)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(Textos.cancelar, action: alCancelar)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(Textos.guardar, action: alGuardar)
                }
            }
        }
        .task { await modelo.comprobar() }
    }

    private func crear() {
        modelo.crearEtiqueta(nueva)
        nueva = ""
    }

    private func alternar(_ etiqueta: String) {
        if modelo.etiquetasPuestas.contains(etiqueta) {
            modelo.etiquetasPuestas.remove(etiqueta)
        } else {
            modelo.etiquetasPuestas.insert(etiqueta)
        }
    }
}

/// Lo que se ve cuando lo compartido no se puede guardar. Un solo botón, y
/// que el texto diga por qué.
struct VistaAviso: View {
    let mensaje: String
    let alCerrar: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text(mensaje)
                    .multilineTextAlignment(.center)
                Button(Textos.cerrar, action: alCerrar)
                    .buttonStyle(.borderedProminent)
            }
            .padding()
            .navigationTitle(Textos.compartirTitulo)
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
