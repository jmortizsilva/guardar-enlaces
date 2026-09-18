import Dominio
import SwiftUI

/// Todo lo que se sabe de un enlace, con sus acciones en botones.
///
/// La lista ya ofrece estas mismas acciones en el rotor, pero ahí el título
/// y la descripción se oyen de corrido dentro de la fila. Aquí cada cosa está
/// en su sitio y se puede recorrer, que es lo que en la app de Windows obligó
/// a poner campos de solo lectura en vez de texto suelto.
struct PantallaDetalle: View {
    let elemento: Elemento
    @Binding var presentada: Bool

    @Environment(ModeloApp.self) private var modelo
    @State private var etiquetasAbiertas = false
    @State private var confirmandoEliminar = false
    @State private var aAbrir: EnlaceAAbrir?

    /// El de la lista puede haber cambiado desde que se abrió esto: al editar
    /// las etiquetas hay que enseñar las nuevas, no las de cuando se entró.
    private var actual: Elemento {
        modelo.elementos.first { $0.id == elemento.id } ?? elemento
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if let titulo = actual.titulo, !titulo.isEmpty {
                        Text(titulo)
                            .font(.headline)
                    }
                    Text(actual.url)
                        .textSelection(.enabled)
                        .accessibilityLabel("\(Textos.campoDireccion): \(actual.url)")
                    if let descripcion = actual.descripcion, !descripcion.isEmpty {
                        Text(descripcion)
                            .accessibilityLabel("\(Textos.campoDescripcion): \(descripcion)")
                    }
                }

                Section {
                    Button {
                        etiquetasAbiertas = true
                    } label: {
                        LabeledContent(Textos.campoEtiquetas) {
                            Text(
                                actual.etiquetas.isEmpty
                                    ? Textos.ningunaEtiqueta
                                    : actual.etiquetas.joined(separator: ", ")
                            )
                        }
                    }
                    .accessibilityHint(Textos.editarEtiquetas)

                    Text(
                        Textos.guardadoEl(
                            Presentacion.fechaLegible(
                                actual.creadoEn,
                                locale: Presentacion.localeDeLaApp,
                                zonaHoraria: .current
                            )
                        )
                    )
                    .foregroundStyle(.secondary)
                }

                Section {
                    Button(Textos.abrirEnModoLector) { abrir(enModoLector: true) }
                    Button(Textos.abrirEnSafari) { abrir(enModoLector: false) }
                    Button(Textos.copiarUrl) {
                        UIPasteboard.general.string = actual.url
                        Anuncios.importante(Textos.urlCopiada)
                    }
                }

                Section {
                    Button(Textos.eliminar, role: .destructive) { confirmandoEliminar = true }
                }
            }
            .navigationTitle(Presentacion.titulo(de: actual))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(Textos.cerrar) { presentada = false }
                }
            }
            .sheet(isPresented: $etiquetasAbiertas) {
                PantallaEtiquetas(
                    disponibles: modelo.etiquetasDisponibles,
                    elegidas: actual.etiquetas
                ) { elegidas in
                    modelo.cambiarEtiquetas(de: actual, a: elegidas)
                }
            }
            .sheet(item: $aAbrir) { enlace in
                VistaSafari(url: enlace.url, modoLector: enlace.modoLector)
                    .ignoresSafeArea()
            }
            .alert(
                Textos.preguntaEliminar(titulo: Presentacion.titulo(de: actual)),
                isPresented: $confirmandoEliminar
            ) {
                Button(Textos.eliminar, role: .destructive) {
                    modelo.eliminar(actual)
                    // Se cierra: quedarse en el detalle de algo que ya no
                    // existe deja a VoiceOver leyendo una ficha fantasma.
                    presentada = false
                }
                Button(Textos.cancelar, role: .cancel) {}
            } message: {
                Text(Textos.consecuenciaEliminar(conCuenta: modelo.autenticado))
            }
        }
    }

    private func abrir(enModoLector: Bool) {
        guard let url = URL(string: actual.url) else {
            return
        }
        aAbrir = EnlaceAAbrir(url: url, modoLector: enModoLector)
    }
}
