import Foundation

public enum TipoElemento: String, Codable, Sendable {
    case enlace
    case video
    case articulo
    case imagen
}

/// Un enlace guardado.
///
/// El identificador lo genera el cliente (uuid v4): permite guardar sin
/// conexión y hace que subirlo dos veces no cree dos elementos. `borrado` es
/// una lápida, no un borrado de verdad: el elemento tiene que seguir
/// existiendo para que los demás dispositivos se enteren de la baja.
///
/// Los campos son `private(set)`: desde fuera es inmutable, y los cambios se
/// piden con los métodos de abajo, que son los que mueven `actualizadoEn`.
/// Olvidarse de esa fecha al cambiar algo es perder el cambio en cuanto
/// sincronice, porque gana el más reciente.
public struct Elemento: Equatable, Sendable, Codable {
    public private(set) var id: String
    public private(set) var url: String
    public private(set) var titulo: String?
    public private(set) var descripcion: String?
    public private(set) var imagenUrl: String?
    public private(set) var tipo: TipoElemento
    public private(set) var etiquetas: [String]
    public private(set) var creadoEn: MarcaDeTiempo
    public private(set) var actualizadoEn: MarcaDeTiempo
    public private(set) var borrado: Bool

    public init(
        id: String,
        url: String,
        titulo: String? = nil,
        descripcion: String? = nil,
        imagenUrl: String? = nil,
        tipo: TipoElemento = .enlace,
        etiquetas: [String] = [],
        creadoEn: MarcaDeTiempo = 0,
        actualizadoEn: MarcaDeTiempo = 0,
        borrado: Bool = false
    ) {
        self.id = id
        self.url = url
        self.titulo = titulo
        self.descripcion = descripcion
        self.imagenUrl = imagenUrl
        self.tipo = tipo
        self.etiquetas = etiquetas
        self.creadoEn = creadoEn
        self.actualizadoEn = actualizadoEn
        self.borrado = borrado
    }

    /// Tolerante con lo que falte, igual que `elementoDesdeJson` en la app de
    /// Expo: una baja que llega del servidor solo trae identificador y fecha,
    /// y el resto se rellena con lo que ya se sabía.
    ///
    /// El identificador sí es obligatorio: un elemento sin él no se puede
    /// guardar, ni comparar, ni volver a subir.
    public init(from decoder: any Decoder) throws {
        let campos = try decoder.container(keyedBy: CodingKeys.self)
        id = try campos.decode(String.self, forKey: .id)
        url = try campos.decodeIfPresent(String.self, forKey: .url) ?? ""
        titulo = try campos.decodeIfPresent(String.self, forKey: .titulo)
        descripcion = try campos.decodeIfPresent(String.self, forKey: .descripcion)
        imagenUrl = try campos.decodeIfPresent(String.self, forKey: .imagenUrl)
        // Un tipo que aquí no se conozca no invalida el elemento: el contrato
        // puede añadir tipos y esta app tiene que seguir guardando el enlace.
        tipo = (try? campos.decodeIfPresent(TipoElemento.self, forKey: .tipo)) ?? .enlace
        etiquetas = try campos.decodeIfPresent([String].self, forKey: .etiquetas) ?? []
        creadoEn = try campos.decodeIfPresent(MarcaDeTiempo.self, forKey: .creadoEn) ?? 0
        actualizadoEn = try campos.decodeIfPresent(MarcaDeTiempo.self, forKey: .actualizadoEn) ?? 0
        borrado = try campos.decodeIfPresent(Bool.self, forKey: .borrado) ?? false
    }
}

public struct DatosElementoNuevo: Sendable {
    public let url: String
    public let titulo: String?
    public let descripcion: String?
    public let imagenUrl: String?
    public let tipo: TipoElemento
    public let etiquetas: [String]

    public init(
        url: String,
        titulo: String? = nil,
        descripcion: String? = nil,
        imagenUrl: String? = nil,
        tipo: TipoElemento = .enlace,
        etiquetas: [String] = []
    ) {
        self.url = url
        self.titulo = titulo
        self.descripcion = descripcion
        self.imagenUrl = imagenUrl
        self.tipo = tipo
        self.etiquetas = etiquetas
    }
}

public func nuevoElementoLocal(
    _ datos: DatosElementoNuevo,
    ahora: Reloj = relojDelSistema,
    generarId: GeneradorId = generarIdUnico
) -> Elemento {
    let instante = ahora()
    return Elemento(
        id: generarId(),
        url: datos.url,
        titulo: datos.titulo,
        descripcion: datos.descripcion,
        imagenUrl: datos.imagenUrl,
        tipo: datos.tipo,
        etiquetas: datos.etiquetas,
        creadoEn: instante,
        actualizadoEn: instante
    )
}

extension Elemento {
    /// Baja lógica: el servidor necesita ver el borrado para avisar a los
    /// demás dispositivos, así que la fila nunca se tira de golpe.
    public func marcadoComoBorrado(ahora: Reloj = relojDelSistema) -> Elemento {
        var copia = self
        copia.borrado = true
        copia.actualizadoEn = ahora()
        return copia
    }

    public func conEtiquetas(_ nuevas: [String], ahora: Reloj = relojDelSistema) -> Elemento {
        var copia = self
        copia.etiquetas = nuevas
        copia.actualizadoEn = ahora()
        return copia
    }

    public func conMetadatos(
        titulo: String?,
        descripcion: String?,
        imagenUrl: String?,
        tipo: TipoElemento,
        ahora: Reloj = relojDelSistema
    ) -> Elemento {
        var copia = self
        copia.titulo = titulo
        copia.descripcion = descripcion
        copia.imagenUrl = imagenUrl
        copia.tipo = tipo
        copia.actualizadoEn = ahora()
        return copia
    }
}
