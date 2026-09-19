import SafariServices
import SwiftUI

/// El visor de Safari dentro de la app, con el modo lector puesto cuando la
/// página lo permite.
///
/// El modo lector es la razón de usar `SFSafariViewController` en vez de
/// abrir la URL y ya está: quita el menú, los anuncios y los avisos de
/// cookies, que con lector de pantalla es la diferencia entre leer el
/// artículo y recorrer media página buscándolo.
struct VistaSafari: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let configuracion = SFSafariViewController.Configuration()
        configuracion.entersReaderIfAvailable = true
        return SFSafariViewController(url: url, configuration: configuracion)
    }

    func updateUIViewController(_ controlador: SFSafariViewController, context: Context) {}
}
