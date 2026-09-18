import Dominio
import SwiftUI

struct PantallaLista: View {
    @Environment(ModeloApp.self) private var modelo

    @State private var busqueda = ""
    @State private var etiqueta: String?
    @State private var aEliminar: Elemento?
    @State private var aEtiquetar: Elemento?
    @State private var aAbrir: EnlaceAAbrir?
    @State private var anadiendo = false

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
                .sheet(isPresented: $anadiendo) {
                    PantallaAnadir(presentada: $anadiendo)
                }
                .sheet(item: $aAbrir) { enlace in
                    VistaSafari(url: enlace.url, modoLector: enlace.modoLector)
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
                    alAbrir: { abrir(elemento, enModoLector: true) },
                    alCopiar: { copiar(elemento) },
                    alAbrirEnSafari: { abrir(elemento, enModoLector: false) },
                    alEtiquetar: { aEtiquetar = elemento },
                    alEliminar: { aEliminar = elemento }
                )
            }
        }
    }

    @ToolbarContentBuilder
    private var barra: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Menu {
                Picker(Textos.filtroPorEtiqueta(etiqueta), selection: $etiqueta) {
                    Text(Textos.todasLasEtiquetas).tag(String?.none)
                    ForEach(modelo.etiquetasDisponibles, id: \.self) { nombre in
                        Text(nombre).tag(String?.some(nombre))
                    }
                }
            } label: {
                Label(Textos.filtroPorEtiqueta(etiqueta), systemImage: "line.3.horizontal.decrease")
            }
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            if modelo.sincronizando {
                ProgressView()
                    .accessibilityLabel(Textos.sincronizando)
            }
            Button {
                anadiendo = true
            } label: {
                Label(Textos.anadirEnlace, systemImage: "plus")
            }
            Button {
                // Ajustes llega en el paso siguiente.
            } label: {
                Label(Textos.ajustes, systemImage: "gearshape")
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

    private func abrir(_ elemento: Elemento, enModoLector: Bool) {
        guard let url = URL(string: elemento.url) else {
            return
        }
        aAbrir = EnlaceAAbrir(url: url, modoLector: enModoLector)
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
    let alAbrirEnSafari: () -> Void
    let alEtiquetar: () -> Void
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
        .accessibilityActions {
            Button(Textos.copiarUrl, action: alCopiar)
            Button(Textos.abrirEnSafari, action: alAbrirEnSafari)
            Button(Textos.editarEtiquetas, action: alEtiquetar)
            Button(Textos.eliminar, action: alEliminar)
        }
        .swipeActions(edge: .trailing) {
            Button(Textos.eliminar, role: .destructive, action: alEliminar)
        }
        .swipeActions(edge: .leading) {
            Button(Textos.editarEtiquetas, action: alEtiquetar)
                .tint(.accentColor)
        }
        .contextMenu {
            Button(Textos.copiarUrl, action: alCopiar)
            Button(Textos.abrirEnSafari, action: alAbrirEnSafari)
            Button(Textos.editarEtiquetas, action: alEtiquetar)
            Button(Textos.eliminar, role: .destructive, action: alEliminar)
        }
    }
}

/// Qué enlace se está abriendo y cómo. Sin el «cómo», «Abrir en Safari»
/// acabaría abriendo igualmente en modo lector: son dos acciones distintas
/// del rotor y tienen que hacer dos cosas distintas.
struct EnlaceAAbrir: Identifiable {
    let url: URL
    let modoLector: Bool

    var id: String { "\(url.absoluteString)|\(modoLector)" }
}

/// Para poder usar `Elemento` con `.alert(item:)` y `.sheet(item:)`.
extension Elemento: @retroactive Identifiable {}
