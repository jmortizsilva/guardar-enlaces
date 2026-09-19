import Dominio
import Fontaneria
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Lo que arranca cuando se comparte un enlace con Guárdalo.
///
/// Sustituye a las 315 líneas de `ShareViewController.swift` de la app de
/// Expo, que tenían que rehacer aquí el llavero, la renovación del token y la
/// sincronización porque una extensión de React Native no comparte código con
/// la aplicación. Aquí sí: son los mismos paquetes, y guardar es una llamada.
final class ControladorCompartir: UIViewController {
    private var modelo: ModeloCompartir?

    override func viewDidLoad() {
        super.viewDidLoad()
        Task { await arrancar() }
    }

    private func arrancar() async {
        guard let url = await urlCompartida() else {
            mostrar(VistaAviso(mensaje: Textos.compartirNoEsEnlace, alCerrar: cerrar))
            return
        }
        guard let modelo = try? ModeloCompartir(url: url) else {
            mostrar(VistaAviso(mensaje: Textos.compartirNoSePudo, alCerrar: cerrar))
            return
        }
        self.modelo = modelo

        if guardadoSilencioso {
            await guardarSinPreguntar(modelo)
            return
        }
        mostrar(
            VistaCompartir(
                modelo: modelo,
                alGuardar: { Task { await self.guardar(modelo) } },
                alCancelar: cerrar
            )
        )
    }

    private var guardadoSilencioso: Bool {
        Configuracion.ajustesCompartidos()?
            .bool(forKey: Configuracion.claveGuardadoSilencioso) ?? false
    }

    // MARK: - Guardar

    /// Con el interruptor puesto no se enseña nada, pero sí se dice: si no,
    /// compartir un enlace no tiene ningún acuse y no se sabe si se guardó.
    private func guardarSinPreguntar(_ modelo: ModeloCompartir) async {
        await modelo.comprobar()
        await guardar(modelo)
    }

    private func guardar(_ modelo: ModeloCompartir) async {
        guard let anuncio = modelo.guardar() else {
            Anuncios.importante(Textos.compartirNoSePudo)
            return
        }
        // Se dice antes de subir: lo que hay que saber ya es verdad, el
        // enlace está guardado. Lo de la red viene después y puede fallar sin
        // que cambie nada.
        Anuncios.importante(anuncio)
        // Y se sube antes de cerrar, aunque la hoja se quede unos instantes:
        // al cerrar, iOS mata la extensión en el acto, así que subir después
        // es no subir nunca. Con el límite, lo peor que pasa es que quede
        // encolado para la próxima sincronización.
        await conLimite(segundos: 3) { await modelo.intentarSubir() }
        cerrar()
    }

    private func conLimite(segundos: Double, _ trabajo: @escaping @Sendable () async -> Void) async
    {
        await withTaskGroup(of: Void.self) { grupo in
            grupo.addTask { await trabajo() }
            grupo.addTask { try? await Task.sleep(for: .seconds(segundos)) }
            await grupo.next()
            grupo.cancelAll()
        }
    }

    private func cerrar() {
        extensionContext?.completeRequest(returningItems: [])
    }

    // MARK: - Lo que llega de la otra aplicación

    /// La dirección compartida, mire por donde mire.
    ///
    /// Safari la entrega como URL, pero otras aplicaciones mandan un texto
    /// con la dirección dentro. Se prueban las dos formas antes de decir que
    /// esto no es un enlace.
    private func urlCompartida() async -> String? {
        let adjuntos =
            (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .flatMap { $0.attachments ?? [] }

        for adjunto in adjuntos {
            if let url = await cargarUrl(de: adjunto), Enlaces.esDireccion(url) {
                return url
            }
            if let texto = await cargarTexto(de: adjunto),
                let url = Enlaces.direccionDentroDe(texto)
            {
                return url
            }
        }
        return nil
    }

    private func cargarUrl(de adjunto: NSItemProvider) async -> String? {
        guard adjunto.hasItemConformingToTypeIdentifier(UTType.url.identifier) else { return nil }
        let item = try? await adjunto.loadItem(forTypeIdentifier: UTType.url.identifier)
        return (item as? URL)?.absoluteString
    }

    private func cargarTexto(de adjunto: NSItemProvider) async -> String? {
        guard adjunto.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) else {
            return nil
        }
        let item = try? await adjunto.loadItem(forTypeIdentifier: UTType.plainText.identifier)
        return item as? String
    }

    // MARK: - Enseñar una vista de SwiftUI

    private func mostrar(_ vista: some View) {
        let alojamiento = UIHostingController(rootView: vista)
        addChild(alojamiento)
        alojamiento.view.frame = view.bounds
        alojamiento.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(alojamiento.view)
        alojamiento.didMove(toParent: self)
    }
}
