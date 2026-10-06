import Foundation

/// Si una página responde. Ver «Comprobar que carga» en `ANADIR.md`.
public enum Comprobacion: Equatable, Sendable {
    /// Responde. Los metadatos pueden venir vacíos: una página sin título
    /// también carga.
    case carga(MetadatosExtraidos?)
    /// Hay conexión, pero la página no responde o responde con un error.
    case noCarga
    /// No se ha podido preguntar: sin red, o el servidor no contesta. Se
    /// guarda sin preguntar.
    case sinComprobar

    public var metadatos: MetadatosExtraidos? {
        if case .carga(let metadatos) = self {
            return metadatos
        }
        return nil
    }
}

/// La dirección que se va a guardar, y lo que se sabe de ella.
public struct DireccionComprobada: Equatable, Sendable {
    public let direccion: String
    public let comprobacion: Comprobacion

    public init(direccion: String, comprobacion: Comprobacion) {
        self.direccion = direccion
        self.comprobacion = comprobacion
    }
}

public enum ComprobarDireccion {
    /// Prueba la dirección y, si no carga y el `https://` lo puso la app, la
    /// alternativa con `http://`: algunas páginas antiguas solo responden
    /// así. Se queda con la que cargue; si no carga ninguna, con la de
    /// `https://`, que es la que se ofrecerá guardar igualmente.
    public static func comprobar(
        _ escrita: DireccionEscrita,
        con comprobar: @Sendable (String) async -> Comprobacion
    ) async -> DireccionComprobada {
        let primera = await comprobar(escrita.direccion)
        guard primera == .noCarga, let alternativa = escrita.alternativa else {
            return DireccionComprobada(direccion: escrita.direccion, comprobacion: primera)
        }
        let segunda = await comprobar(alternativa)
        if case .carga = segunda {
            return DireccionComprobada(direccion: alternativa, comprobacion: segunda)
        }
        return DireccionComprobada(direccion: escrita.direccion, comprobacion: primera)
    }
}
