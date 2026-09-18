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

    /// Solo contra un servidor con `PERMITIR_LOGIN_DEV=true`.
    public func entrarComoDesarrollo(email: String) async throws {
        try aplicar(await cliente.loginDeDesarrollo(email: email))
    }

    /// Recupera la sesión con el token guardado de una vez anterior.
    /// Devuelve `false` si no había, o si ya no sirve.
    public func restaurar() async -> Bool {
        guard let guardado = try? credenciales.tokenRefresco(), !guardado.isEmpty else {
            return false
        }
        do {
            try aplicar(await cliente.renovar(tokenRefresco: guardado))
            return true
        } catch {
            // Da igual por qué falló: un token que no sirve no va a servir
            // luego, y dejarlo hace que cada arranque intente renovar en
            // vano. Se borra y se entra como si no hubiera cuenta.
            try? credenciales.borrarTokenRefresco()
            tokenAcceso = nil
            usuario = nil
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
