import Foundation

/// Una etiqueta que existe por sí sola, sin que ningún enlace la lleve
/// todavía: sirve para crearla en el iPhone y verla en el ordenador antes de usarla.
///
/// Se sincroniza igual que un elemento (identificador del cliente, lápida,
/// gana la fecha más reciente), en la misma llamada y bajo la clave
/// `etiquetasDefinidas` del contrato. Sin `url` ni los demás campos, que aquí
/// no significan nada.
public struct EtiquetaDefinida: Equatable, Sendable, Codable {
    public private(set) var id: String
    public private(set) var nombre: String
    public private(set) var creadoEn: MarcaDeTiempo
    public private(set) var actualizadoEn: MarcaDeTiempo
    public private(set) var borrado: Bool

    public init(
        id: String,
        nombre: String,
        creadoEn: MarcaDeTiempo = 0,
        actualizadoEn: MarcaDeTiempo = 0,
        borrado: Bool = false
    ) {
        self.id = id
        self.nombre = nombre
        self.creadoEn = creadoEn
        self.actualizadoEn = actualizadoEn
        self.borrado = borrado
    }

    /// Tolerante con lo que falte, por el mismo motivo que en `Elemento`: una
    /// baja que llega del servidor solo trae identificador y fecha.
    public init(from decoder: any Decoder) throws {
        let campos = try decoder.container(keyedBy: CodingKeys.self)
        id = try campos.decode(String.self, forKey: .id)
        nombre = try campos.decodeIfPresent(String.self, forKey: .nombre) ?? ""
        creadoEn = try campos.decodeIfPresent(MarcaDeTiempo.self, forKey: .creadoEn) ?? 0
        actualizadoEn = try campos.decodeIfPresent(MarcaDeTiempo.self, forKey: .actualizadoEn) ?? 0
        borrado = try campos.decodeIfPresent(Bool.self, forKey: .borrado) ?? false
    }
}

public func nuevaEtiquetaDefinida(
    nombre: String,
    ahora: Reloj = relojDelSistema,
    generarId: GeneradorId = generarIdUnico
) -> EtiquetaDefinida {
    let instante = ahora()
    return EtiquetaDefinida(
        id: generarId(),
        nombre: nombre,
        creadoEn: instante,
        actualizadoEn: instante
    )
}

extension EtiquetaDefinida {
    public func renombrada(_ nuevoNombre: String, ahora: Reloj = relojDelSistema)
        -> EtiquetaDefinida
    {
        var copia = self
        copia.nombre = nuevoNombre
        copia.actualizadoEn = ahora()
        return copia
    }

    public func marcadaComoBorrada(ahora: Reloj = relojDelSistema) -> EtiquetaDefinida {
        var copia = self
        copia.borrado = true
        copia.actualizadoEn = ahora()
        return copia
    }
}
