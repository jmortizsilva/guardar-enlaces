import Dominio
import Foundation

/// La sesión: token de acceso en memoria, token de refresco en el llavero.
///
/// Es un actor porque la tocan a la vez la pantalla (entrar, salir) y las
/// sincronizaciones de fondo, y dos renovaciones simultáneas con el mismo
/// token de refresco harían que el servidor revocara la segunda: la rotación
/// invalida el token en cuanto se usa una vez.
public actor Sesion {
    private let cliente: ClienteApi
    private let credenciales: AlmacenCredenciales

    public private(set) var tokenAcceso: String?
    public private(set) var usuario: UsuarioApi?

    public init(cliente: ClienteApi, credenciales: AlmacenCredenciales) {
        self.cliente = cliente
        self.credenciales = credenciales
    }

    public var autenticado: Bool { tokenAcceso != nil }

    /// Cambia el código que trae el navegador por los tokens de sesión.
    public func entrar(conCodigoDeCanje codigo: String) async throws {
        try aplicar(await cliente.canjear(codigoCanje: codigo))
    }

    /// Entra con el token que ha devuelto Apple en el propio teléfono.
    public func entrarConApple(identityToken: String, nonce: String) async throws {
        try aplicar(await cliente.entrarConApple(identityToken: identityToken, nonce: nonce))
    }

    /// Solo contra un servidor con `PERMITIR_LOGIN_DEV=true`.
    public func entrarComoDesarrollo(email: String) async throws {
        try aplicar(await cliente.loginDeDesarrollo(email: email))
    }

    /// Si hay un token guardado de una vez anterior, se haya podido renovar o
    /// no. Sirve para saber que hay que reintentar cuando vuelva la red.
    public var tieneSesionGuardada: Bool {
        guard let guardado = try? credenciales.tokenRefresco() else { return false }
        return !guardado.isEmpty
    }

    /// La renovación que está en marcha, si hay una. Ver `restaurar()`.
    private var renovando: Task<Bool, Never>?

    /// Recupera la sesión con el token guardado de una vez anterior.
    /// Devuelve `false` si no había, si ya no sirve o si no se pudo preguntar.
    ///
    /// Una sola renovación a la vez, aunque la pidan varios. Que esto sea un
    /// actor no bastaba: mientras una espera a la red, el actor deja entrar a
    /// otra. Dos peticiones con el token caducado renovaban las dos con el
    /// mismo token de refresco; el servidor rota en el primer uso y rechaza el
    /// segundo, y ese rechazo borraba el token bueno que acababa de guardar
    /// la primera. Se perdía la sesión justo al sincronizar y comprobar una
    /// página a la vez. Reproducido en `PruebasSesionFallos`.
    public func restaurar() async -> Bool {
        if let enMarcha = renovando {
            return await enMarcha.value
        }
        let tarea = Task { await self.renovarConElTokenGuardado() }
        renovando = tarea
        let resultado = await tarea.value
        renovando = nil
        return resultado
    }

    private func renovarConElTokenGuardado() async -> Bool {
        guard let guardado = try? credenciales.tokenRefresco(), !guardado.isEmpty else {
            return false
        }
        do {
            try aplicar(await cliente.renovar(tokenRefresco: guardado))
            return true
        } catch let rechazo as ErrorApi where rechazo.esSesionCaducada {
            // El servidor dice que ese token ya no sirve: no va a servir luego,
            // y dejarlo haría que cada arranque intentara renovar en vano.
            try? credenciales.borrarTokenRefresco()
            tokenAcceso = nil
            usuario = nil
            return false
        } catch {
            // Cualquier otra cosa, y sobre todo no tener red, no dice nada de
            // si el token sirve. Antes se borraba igual, y abrir la app sin
            // cobertura cerraba la sesión. Se deja donde está y se reintenta.
            return false
        }
    }

    public func cerrar() async {
        if let guardado = try? credenciales.tokenRefresco(), !guardado.isEmpty {
            // Si el servidor no contesta, la sesión se cierra aquí igual: lo
            // que no puede pasar es que el botón no haga nada.
            try? await cliente.cerrarSesion(tokenRefresco: guardado)
        }
        try? credenciales.borrarTokenRefresco()
        tokenAcceso = nil
        usuario = nil
    }

    /// Ejecuta algo que necesita el token de acceso y, si el servidor dice
    /// que ha caducado, lo renueva una vez y lo vuelve a intentar. Si la
    /// renovación también falla, el fallo se propaga: ahí ya toca volver a
    /// iniciar sesión.
    public func conReintento<T: Sendable>(
        _ peticion: @Sendable (String) async throws -> T
    ) async throws -> T {
        guard let token = tokenAcceso else {
            throw ErrorApi(mensaje: "no hay ninguna sesión iniciada", codigo: 401)
        }
        do {
            return try await peticion(token)
        } catch let fallo as ErrorApi where fallo.esSesionCaducada {
            // Mientras esta esperaba, otra puede haber renovado ya: entonces
            // no hay que renovar otra vez, solo usar el token nuevo.
            if let actual = tokenAcceso, actual != token {
                return try await peticion(actual)
            }
            guard await restaurar(), let renovado = tokenAcceso else {
                throw fallo
            }
            return try await peticion(renovado)
        }
    }

    private func aplicar(_ respuesta: RespuestaCanje) throws {
        tokenAcceso = respuesta.tokenAcceso
        if let recibido = respuesta.usuario {
            usuario = recibido
        }
        // La rotación obliga a guardar el token nuevo: el anterior queda
        // revocado en cuanto se usa, así que perderlo aquí deja la sesión sin
        // forma de renovarse en el siguiente arranque.
        try credenciales.guardarTokenRefresco(respuesta.tokenRefresco)
    }
}
