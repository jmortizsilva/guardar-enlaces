import Dominio
import Foundation
import Testing

@testable import Fontaneria

/// Tres fallos de sesión que se vieron al portar este código a Android, y que
/// estaban apuntados sin comprobar. Estas pruebas los reproducen: se
/// escribieron antes de tocar `Sesion`, y fallaban.

/// Cuenta las renovaciones que llegan al servidor, y contesta como el de
/// verdad: la primera con tokens nuevos, y cualquier otra con el token ya
/// usado, que el servidor rechaza con un 401 porque la rotación lo revocó.
private final class ServidorQueRota: @unchecked Sendable {
    private let cerrojo = NSLock()
    private var _renovaciones = 0

    var renovaciones: Int { cerrojo.withLock { _renovaciones } }

    func responder(_ peticion: URLRequest) -> ServidorFalso.Respuesta {
        if peticion.url?.path == "/auth/dev-login" {
            // Para empezar con una sesión iniciada sin tocar Sesion por dentro.
            return .json(
                """
                {"tokenAcceso": "acceso-caducado", "expiraEn": 1735689600000,
                 "tokenRefresco": "refresco-1"}
                """)
        }
        guard peticion.url?.path == "/auth/renovar" else { return .json("{}") }
        let numero = cerrojo.withLock {
            _renovaciones += 1
            return _renovaciones
        }
        if numero == 1 {
            return .json(
                """
                {"tokenAcceso": "acceso-renovado", "expiraEn": 1735689600000,
                 "tokenRefresco": "refresco-2"}
                """)
        }
        return .json(#"{"error": "token de refresco no valido"}"#, codigo: 401)
    }
}

@Suite("Sesión: fallos que se vieron al portar a Android")
struct PruebasSesionFallos {
    private let cliente: ClienteApi
    private let buzon: ServidorFalso.Buzon

    init() {
        let (sesionHttp, buzon) = ServidorFalso.sesion()
        self.buzon = buzon
        cliente = ClienteApi(urlBase: "https://api.ejemplo.com", sesionHttp: sesionHttp)
    }

    @Test("abrir la app sin conexión no cierra la sesión")
    func sinConexionNoSeBorraElToken() async throws {
        // El caso de todos los días: abrir la app en el metro. Que no haya red
        // no dice nada de si el token sirve; borrarlo obligaba a volver a
        // entrar con la cuenta en cuanto hubiera cobertura.
        buzon.fallarLaConexion = true
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco-guardado")
        let sesion = Sesion(cliente: cliente, credenciales: llavero)

        _ = await sesion.restaurar()

        #expect(try llavero.tokenRefresco() == "refresco-guardado")
    }

    @Test("un token que el servidor rechaza sí se borra")
    func tokenRechazadoSeBorra() async throws {
        // La otra cara: esto es lo que la limpieza sí tenía que hacer, y tiene
        // que seguir haciéndolo. Si no, cada arranque renueva en vano.
        buzon.respuesta = .json(#"{"error": "token de refresco no valido"}"#, codigo: 401)
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco-revocado")
        let sesion = Sesion(cliente: cliente, credenciales: llavero)

        let recuperada = await sesion.restaurar()

        #expect(!recuperada)
        #expect(try llavero.tokenRefresco() == nil)
    }

    @Test("dos peticiones con el token caducado renuevan una sola vez")
    func dosCaducadasUnaRenovacion() async throws {
        let servidor = ServidorQueRota()
        buzon.manejador = { servidor.responder($0) }
        let llavero = CredencialesEnMemoria()
        let sesion = Sesion(cliente: cliente, credenciales: llavero)
        try await sesion.entrarComoDesarrollo(email: "persona@ejemplo.com")

        // Una petición que tarda un poco y devuelve 401 con el token viejo: el
        // tiempo justo para que la segunda entre en el actor mientras la
        // primera espera, que es lo que pasa al sincronizar y comprobar una
        // página a la vez.
        let peticion: @Sendable (String) async throws -> String = { token in
            if token == "acceso-caducado" {
                try await Task.sleep(for: .milliseconds(20))
                throw ErrorApi(mensaje: "caducado", codigo: 401)
            }
            return token
        }

        async let una = sesion.conReintento(peticion)
        async let otra = sesion.conReintento(peticion)
        let resultados = try await [una, otra]

        #expect(resultados == ["acceso-renovado", "acceso-renovado"])
        #expect(servidor.renovaciones == 1)
        #expect(await sesion.autenticado)
        #expect(try llavero.tokenRefresco() == "refresco-2")
    }
}
