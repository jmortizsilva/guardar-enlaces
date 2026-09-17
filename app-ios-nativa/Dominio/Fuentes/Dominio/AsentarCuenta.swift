import Foundation

/// Los enlaces que ya había en el teléfono cuando alguien inicia sesión.
public struct EnlacesEnElTelefono: Equatable, Sendable {
    public let cuantos: Int
    /// `true` si son de otra cuenta; `false` si se guardaron sin cuenta.
    public let deOtraCuenta: Bool

    public init(cuantos: Int, deOtraCuenta: Bool) {
        self.cuantos = cuantos
        self.deOtraCuenta = deOtraCuenta
    }
}

/// Qué hacer con lo que ya hay guardado.
public enum AsientoDeCuenta: Equatable, Sendable {
    /// Darles identificadores nuevos y subirlos a esta cuenta. Los nuevos
    /// identificadores no son un capricho: si venían de otra cuenta, el
    /// servidor ya tiene esos mismos a nombre de su dueño anterior y rechaza
    /// el cambio, así que se quedarían reintentándose para siempre.
    case adoptarLoQueHay
    /// Borrarlos y empezar limpio con esta cuenta.
    case empezarDeCero
}

/// El primer paso al entrar: puede que no haya nada que hacer, que esté claro
/// qué hacer, o que lo tenga que decidir quien está delante.
public enum PasoAlEntrar: Equatable, Sendable {
    /// La cuenta de siempre: no se pregunta ni se toca nada. Es el caso
    /// normal, y tiene que seguir siéndolo.
    case noHacerNada
    case asentar(AsientoDeCuenta)
    case preguntar(EnlacesEnElTelefono)
}

/// La caché local no está separada por cuenta, así que al entrar hay que
/// resolver de quién es lo que hay: o se importa a la cuenta nueva, o se
/// borra.
///
/// Esto es una función pura a propósito, y ahí se separa de la app de Expo,
/// donde `asentarCuenta` recibía el almacén y una promesa que preguntaba al
/// usuario, y había que probarla con un almacén de mentira. Aquí se decide
/// qué hay que hacer, y hacerlo es cosa de quien tiene el almacén delante:
/// se prueba entera sin simulacros.
public enum AsentarCuenta {
    /// El «dueño» de la caché. Lleva la dirección del servidor además del
    /// correo: el mismo correo en el servidor de pruebas y en el de verdad
    /// son dos bibliotecas distintas, y mezclarlas sería peor que empezar de
    /// cero.
    public static func identidadDueno(urlServidor: String, email: String) -> String {
        "\(urlServidor)|\(email)"
    }

    public static func alEntrar(
        duenoAnterior: String?,
        dueno: String,
        cuantosElementos: Int
    ) -> PasoAlEntrar {
        if duenoAnterior == dueno {
            return .noHacerNada
        }
        if cuantosElementos == 0 {
            return .asentar(.empezarDeCero)
        }
        // Se pregunta en vez de decidirlo aquí porque las dos respuestas son
        // razonables y ninguna de las dos se puede deshacer.
        return .preguntar(
            EnlacesEnElTelefono(cuantos: cuantosElementos, deOtraCuenta: duenoAnterior != nil)
        )
    }

    public static func asiento(segunRespuesta importar: Bool) -> AsientoDeCuenta {
        importar ? .adoptarLoQueHay : .empezarDeCero
    }
}
