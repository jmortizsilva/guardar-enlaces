import Dominio
import SwiftUI

struct PantallaLista: View {
    @Environment(ModeloApp.self) private var modelo

    @State private var busqueda = ""
    @State private var etiqueta: String?
    @State private var aEliminar: Elemento?
    @State private var aEtiquetar: Elemento?
    @State private var enDetalle: Elemento?
    @State private var aAbrir: EnlaceAAbrir?
    @State private var anadiendo = false
    @State private var enAjustes = false
    @State private var gestionandoEtiquetas = false

    private var visibles: [Elemento] {
        Biblioteca.buscar(
            Biblioteca.filtrarPorEtiqueta(modelo.elementos, etiqueta: etiqueta),
            texto: busqueda
        )
    }

    var body: some View {
        NavigationStack {
            contenido
                .navigationTitle(Textos.tituloApp)
                .navigationBarTitleDisplayMode(.inline)
                .searchable(text: $busqueda, prompt: Textos.marcadorBusqueda)
                .refreshable { await modelo.sincronizarAMano() }
                .toolbar { barra }
                .alert(item: $aEliminar) { elemento in
                    alertaDeEliminar(elemento)
                }
                .sheet(item: $aEtiquetar) { elemento in
                    PantallaEtiquetas(
                        disponibles: modelo.etiquetasDisponibles,
                        elegidas: elemento.etiquetas
                    ) { etiquetas in
                        modelo.cambiarEtiquetas(de: elemento, a: etiquetas)
                    }
                }
                .sheet(item: $enDetalle) { elemento in
                    PantallaDetalle(
                        elemento: elemento,
                        presentada: Binding(
                            get: { enDetalle != nil },
                            set: { if !$0 { enDetalle = nil } }
                        )
                    )
                }
                .sheet(isPresented: $gestionandoEtiquetas) {
                    PantallaGestionEtiquetas(presentada: $gestionandoEtiquetas)
                }
                .sheet(isPresented: $enAjustes) {
                    PantallaAjustes(presentada: $enAjustes)
                }
                .sheet(isPresented: $anadiendo) {
                    PantallaAnadir(presentada: $anadiendo)
                }
                .sheet(item: $aAbrir) { enlace in
                    VistaSafari(url: enlace.url)
                        .ignoresSafeArea()
                }
        }
    }

    @ViewBuilder
    private var contenido: some View {
        if visibles.isEmpty {
            ContentUnavailableView {
                Text(Textos.listaVacia(busqueda: busqueda, etiqueta: etiqueta))
            }
        } else {
            List(visibles, id: \.id) { elemento in
                FilaEnlace(
                    elemento: elemento,
                    alAbrir: { abrir(elemento) },
                    alCopiar: { copiar(elemento) },
                    alEtiquetar: { aEtiquetar = elemento },
                    alVerDetalles: { enDetalle = elemento },
                    alEliminar: { aEliminar = elemento }
                )
            }
        }
    }

    @ToolbarContentBuilder
    private var barra: some ToolbarContent {
        // Todo a la derecha y el título en la propia barra: así VoiceOver lee
        // «Guárdalo», Ajustes, Añadir enlace y el filtro, en ese orden. Con el
        // título grande, los botones se leían antes que el nombre de la
        // pantalla.
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button {
                enAjustes = true
            } label: {
                Label(Textos.ajustes, systemImage: "gearshape")
            }
            Button {
                anadiendo = true
            } label: {
                Label(Textos.anadirEnlace, systemImage: "plus")
            }
            Menu {
                Picker(Textos.filtroPorEtiqueta(etiqueta), selection: $etiqueta) {
                    Text(Textos.todasLasEtiquetas).tag(String?.none)
                    ForEach(modelo.etiquetasDisponibles, id: \.self) { nombre in
                        Text(nombre).tag(String?.some(nombre))
                    }
                }
                Divider()
                Button(Textos.gestionarEtiquetas) { gestionandoEtiquetas = true }
            } label: {
                Label(Textos.filtroPorEtiqueta(etiqueta), systemImage: "line.3.horizontal.decrease")
            }
            if modelo.sincronizando {
                ProgressView()
                    .accessibilityLabel(Textos.sincronizando)
            }
        }
    }

    private func alertaDeEliminar(_ elemento: Elemento) -> Alert {
        Alert(
            title: Text(Textos.preguntaEliminar(titulo: Presentacion.titulo(de: elemento))),
            message: Text(Textos.consecuenciaEliminar(conCuenta: modelo.autenticado)),
            primaryButton: .destructive(Text(Textos.eliminar)) {
                modelo.eliminar(elemento)
            },
            secondaryButton: .cancel(Text(Textos.cancelar))
        )
    }

    /// Siempre en modo lector y siempre dentro de la app. Quien quiera la
    /// página en otro navegador, copia la dirección: una acción menos que
    /// explicar y que recorrer en el rotor.
    private func abrir(_ elemento: Elemento) {
        guard let url = URL(string: elemento.url) else {
            return
        }
        aAbrir = EnlaceAAbrir(url: url)
    }

    private func copiar(_ elemento: Elemento) {
        UIPasteboard.general.string = elemento.url
        Anuncios.importante(Textos.urlCopiada)
    }
}

/// Una fila es un solo elemento para VoiceOver: se lee «título. dominio,
/// etiquetas y fecha», el doble toque abre en modo lector, y lo demás está en
/// el rotor de acciones.
struct FilaEnlace: View {
    let elemento: Elemento
    let alAbrir: () -> Void
    let alCopiar: () -> Void
    let alEtiquetar: () -> Void
    let alVerDetalles: () -> Void
    let alEliminar: () -> Void

    var body: some View {
        Button(action: alAbrir) {
            VStack(alignment: .leading, spacing: 2) {
                Text(Presentacion.titulo(de: elemento))
                    .font(.headline)
                Text(Presentacion.subtitulo(de: elemento))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundStyle(.primary)
        .accessibilityLabel(
            "\(Presentacion.titulo(de: elemento)). \(Presentacion.subtitulo(de: elemento))"
        )
        .accessibilityHint(Textos.abrirEnModoLector)
        // Aquí NO van eliminar ni ver detalles: lo que se pone en el gesto de
        // deslizar, iOS lo añade solo al rotor, y ponerlo también aquí es lo
        // que hacía que se oyeran dos veces.
        .accessibilityActions {
            Button(Textos.editarEtiquetas, action: alEtiquetar)
            Button(Textos.copiarUrl, action: alCopiar)
        }
        // Eliminar hacia la izquierda, que es donde lo tiene todo iPhone.
        .swipeActions(edge: .trailing) {
            Button(Textos.eliminar, role: .destructive, action: alEliminar)
        }
        .swipeActions(edge: .leading) {
            Button(Textos.verDetalles, action: alVerDetalles)
                .tint(.accentColor)
        }
        // Las cinco, para quien ni desliza ni usa el rotor.
        .contextMenu {
            Button(Textos.verDetalles, action: alVerDetalles)
            Button(Textos.editarEtiquetas, action: alEtiquetar)
            Button(Textos.copiarUrl, action: alCopiar)
            Button(Textos.eliminar, role: .destructive, action: alEliminar)
        }
    }
}

/// Qué enlace se está abriendo y cómo. Sin el «cómo», «Abrir en Safari»
/// acabaría abriendo igualmente en modo lector: son dos acciones distintas
/// del rotor y tienen que hacer dos cosas distintas.
struct EnlaceAAbrir: Identifiable {
    let url: URL

    var id: String { url.absoluteString }
}

/// Para poder usar `Elemento` con `.alert(item:)` y `.sheet(item:)`.
extension Elemento: @retroactive Identifiable {}
