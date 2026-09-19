import Dominio
import Foundation

/// Crear una etiqueta que todavía no lleva ningún enlace.
///
/// Igual que `GuardarEnlace`, esto lo hacen la aplicación y la extensión de
/// compartir, y tiene que ser lo mismo: una etiqueta creada al vuelo desde
/// Safari es exactamente una etiqueta creada al vuelo desde la pantalla de
/// añadir.
public enum CrearEtiqueta {
    /// Devuelve la etiqueta creada, o `nil` si no había nada que crear: el
    /// nombre en blanco, o una que ya existía. Las dos cosas son ruido, no
    /// errores, y quien llama no tiene que distinguirlas.
    public static func crear(
        nombre: String,
        entre existentes: [String],
        en almacen: AlmacenLocal,
        ahora: @escaping Reloj = relojDelSistema
    ) throws -> EtiquetaDefinida? {
        let limpio = nombre.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !limpio.isEmpty, !existentes.contains(limpio) else {
            return nil
        }
        let etiqueta = nuevaEtiquetaDefinida(nombre: limpio, ahora: ahora)
        try almacen.marcarEtiquetaPendiente(etiqueta)
        return etiqueta
    }
}
