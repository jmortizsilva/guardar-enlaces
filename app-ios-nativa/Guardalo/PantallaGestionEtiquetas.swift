import Dominio
import SwiftUI

/// Renombrar o eliminar una etiqueta en todos los enlaces a la vez, y crear
/// las que se quieran tener listas antes de usarlas.
struct PantallaGestionEtiquetas: View {
    @Environment(ModeloApp.self) private var modelo
    @Binding var presentada: Bool

    @State private var nueva = ""
    @State private var renombrando: String?
    @State private var nombreNuevo = ""
    @State private var eliminando: String?

    private var etiquetas: [(nombre: String, enlaces: Int)] {
        modelo.etiquetasConRecuento
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(etiquetas, id: \.nombre) { etiqueta in
                        // La fila entera se lee de una vez, con su recuento:
                        // sin él hay que salir a contar los enlaces antes de
                        // decidir si renombrarla o tirarla.
                        // Mismo criterio que la lista de enlaces: el rotor se
                        // sirve solo desde `accessibilityActions` y el menú
                        // contextual es para quien mira la pantalla. Nada de
                        // gestos de deslizar. Ver docs/ACCESIBILIDAD.md.
                        Text(Textos.etiquetaConRecuento(etiqueta.nombre, enlaces: etiqueta.enlaces))
                            .contextMenu {
                                Button(Textos.renombrar) {
                                    nombreNuevo = etiqueta.nombre
                                    renombrando = etiqueta.nombre
                                }
                                Button(Textos.eliminar, role: .destructive) {
                                    eliminando = etiqueta.nombre
                                }
                            }
                            // Al revés de como se oyen.
                            .accessibilityActions {
                                Button(Textos.eliminar) { eliminando = etiqueta.nombre }
                                Button(Textos.renombrar) {
                                    nombreNuevo = etiqueta.nombre
                                    renombrando = etiqueta.nombre
                                }
                            }
                            // Al revés de como se oyen.
                            .accessibilityActions {
                                Button(Textos.eliminar) { eliminando = etiqueta.nombre }
                                Button(Textos.renombrar) {
                                    nombreNuevo = etiqueta.nombre
                                    renombrando = etiqueta.nombre
                                }
                            }
                    }
                }

                Section {
                    HStack {
                        TextField(Textos.nuevaEtiqueta, text: $nueva)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .onSubmit(crear)
                        Button(Textos.crearEtiqueta, action: crear)
                            .disabled(nueva.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .navigationTitle(Textos.gestionarEtiquetas)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(Textos.cerrar) { presentada = false }
                }
            }
            .alert(
                Textos.renombrar,
                isPresented: Binding(
                    get: { renombrando != nil },
                    set: { if !$0 { renombrando = nil } }
                )
            ) {
                TextField(Textos.nuevoNombre, text: $nombreNuevo)
                    .autocorrectionDisabled()
                Button(Textos.guardar) {
                    if let vieja = renombrando {
                        modelo.renombrarEtiqueta(vieja, a: nombreNuevo)
                    }
                    renombrando = nil
                }
                Button(Textos.cancelar, role: .cancel) { renombrando = nil }
            }
            .alert(
                Textos.eliminar,
                isPresented: Binding(
                    get: { eliminando != nil },
                    set: { if !$0 { eliminando = nil } }
                ),
                presenting: eliminando
            ) { nombre in
                Button(Textos.eliminar, role: .destructive) {
                    modelo.eliminarEtiqueta(nombre)
                    eliminando = nil
                }
                Button(Textos.cancelar, role: .cancel) { eliminando = nil }
            } message: { nombre in
                Text(
                    Textos.preguntaEliminarEtiqueta(
                        nombre,
                        enlaces: etiquetas.first { $0.nombre == nombre }?.enlaces ?? 0
                    )
                )
            }
        }
    }

    private func crear() {
        modelo.crearEtiqueta(nueva)
        nueva = ""
    }
}
