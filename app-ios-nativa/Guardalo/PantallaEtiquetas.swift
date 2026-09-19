import Dominio
import SwiftUI

/// Elegir las etiquetas de un enlace, y crear alguna que todavía no exista.
struct PantallaEtiquetas: View {
    let disponibles: [String]
    @State private var elegidas: Set<String>
    @State private var nueva = ""
    /// Las creadas aquí mismo, para que aparezcan en la lista aunque no las
    /// lleve ningún otro enlace todavía.
    @State private var recienCreadas: [String] = []
    let alGuardar: ([String]) -> Void

    @Environment(\.dismiss) private var cerrar

    init(disponibles: [String], elegidas: [String], alGuardar: @escaping ([String]) -> Void) {
        self.disponibles = disponibles
        self.alGuardar = alGuardar
        _elegidas = State(initialValue: Set(elegidas))
    }

    private var todas: [String] {
        Array(Set(disponibles + recienCreadas + elegidas))
            .sorted { $0.localizedCompare($1) == .orderedAscending }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(todas, id: \.self) { etiqueta in
                        Button {
                            alternar(etiqueta)
                        } label: {
                            HStack {
                                Text(etiqueta)
                                Spacer()
                                if elegidas.contains(etiqueta) {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.tint)
                                }
                            }
                        }
                        .foregroundStyle(.primary)
                        // Que VoiceOver diga «seleccionado» en vez de dejar
                        // la marca de verificación como un dibujo suelto.
                        .accessibilityAddTraits(
                            elegidas.contains(etiqueta) ? [.isSelected] : []
                        )
                    }
                }

                Section {
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
            .navigationTitle(Textos.editarEtiquetas)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(Textos.cancelar) { cerrar() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(Textos.guardar) {
                        alGuardar(
                            elegidas.sorted { $0.localizedCompare($1) == .orderedAscending }
                        )
                        cerrar()
                    }
                }
            }
        }
    }

    private func alternar(_ etiqueta: String) {
        if elegidas.contains(etiqueta) {
            elegidas.remove(etiqueta)
        } else {
            elegidas.insert(etiqueta)
        }
    }

    private func crear() {
        let limpia = nueva.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !limpia.isEmpty else {
            return
        }
        if !todas.contains(limpia) {
            recienCreadas.append(limpia)
        }
        elegidas.insert(limpia)
        nueva = ""
    }
}
