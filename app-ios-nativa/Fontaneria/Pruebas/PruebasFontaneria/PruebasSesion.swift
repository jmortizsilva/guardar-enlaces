import Dominio
import Foundation
import Testing

@testable import Fontaneria

private let respuestaConTokens = """
    {"tokenAcceso": "acceso-nuevo", "expiraEn": 1735689600000,
     "tokenRefresco": "refresco-nuevo",
     "usuario": {"id": 1, "email": "persona@ejemplo.com", "proveedor": "google"}}
    """

@Suite("Sesión")
struct PruebasSesion {
    private let cliente: ClienteApi
    private let buzon: ServidorFalso.Buzon

    init() {
        let (sesionHttp, buzon) = ServidorFalso.sesion()
        self.buzon = buzon
        cliente = ClienteApi(urlBase: "https://api.ejemplo.com", sesionHttp: sesionHttp)
    }

    private func sesion(con llavero: CredencialesEnMemoria) -> Sesion {
        Sesion(cliente: cliente, credenciales: llavero)
    }

    @Test("sin nada guardado no se molesta ni en preguntar al servidor")
    func sinTokenGuardado() async {
        let llavero = CredencialesEnMemoria()

        let recuperada = await sesion(con: llavero).restaurar()

        #expect(recuperada == false)
        #expect(buzon.rutasPedidas.isEmpty)
    }

    @Test("con un token bueno se recupera la sesión y se sabe de quién es")
    func restaurarConTokenBueno() async throws {
        buzon.respuesta = .json(respuestaConTokens)
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco-viejo")
        let sesion = sesion(con: llavero)

        let recuperada = await sesion.restaurar()

        #expect(recuperada)
        #expect(await sesion.autenticado)
        #expect(await sesion.usuario?.email == "persona@ejemplo.com")
    }

    @Test("el token rotado se guarda: si no, el arranque siguiente no entra")
    func guardaElTokenRotado() async throws {
        buzon.respuesta = .json(respuestaConTokens)
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco-viejo")

        _ = await sesion(con: llavero).restaurar()

        #expect(try llavero.tokenRefresco() == "refresco-nuevo")
        #expect(llavero.escrituras == 1)
    }

    @Test("un token que ya no sirve se tira, en vez de reintentarlo cada vez")
    func tokenCaducado() async throws {
        buzon.respuesta = .json(#"{"error": "token revocado"}"#, codigo: 401)
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco-viejo")
        let sesion = sesion(con: llavero)

        let recuperada = await sesion.restaurar()

        #expect(recuperada == false)
        #expect(await sesion.autenticado == false)
        #expect(try llavero.tokenRefresco() == nil)
    }

    @Test("ante un token de acceso caducado, renueva una vez y lo vuelve a intentar")
    func renuevaYReintenta() async throws {
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco-viejo")
        let sesion = sesion(con: llavero)
        buzon.respuesta = .json(respuestaConTokens)
        _ = await sesion.restaurar()

        // A partir de aquí: la primera petición con el token de acceso viejo
        // da 401, la renovación va bien, y la segunda con el token nuevo pasa.
        let intentos = Contador()
        buzon.manejador = { peticion in
            if peticion.url?.path == "/auth/renovar" {
                return .json(respuestaConTokens)
            }
            return intentos.siguiente() == 1
                ? .json(#"{"error": "token caducado"}"#, codigo: 401)
                : .json(#"{"titulo": "Salió a la segunda", "tipo": "enlace"}"#)
        }

        let metadatos = try await sesion.conReintento { token in
            try await cliente.metadatos(url: "https://a.com", tokenAcceso: token)
        }

        #expect(metadatos.titulo == "Salió a la segunda")
        #expect(buzon.rutasPedidas.filter { $0 == "/metadatos" }.count == 2)
    }

    @Test("si la renovación también falla, el fallo llega a la pantalla")
    func renovacionFallida() async throws {
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco-viejo")
        let sesion = sesion(con: llavero)
        buzon.respuesta = .json(respuestaConTokens)
        _ = await sesion.restaurar()

        buzon.respuesta = .json(#"{"error": "sesión no válida"}"#, codigo: 401)

        await #expect(throws: ErrorApi.self) {
            try await sesion.conReintento { token in
                try await cliente.metadatos(url: "https://a.com", tokenAcceso: token)
            }
        }
        #expect(await sesion.autenticado == false)
    }

    @Test("sin sesión iniciada no se inventa una petición sin token")
    func sinSesion() async {
        let sesion = sesion(con: CredencialesEnMemoria())

        await #expect(throws: ErrorApi.self) {
            try await sesion.conReintento { token in
                try await cliente.metadatos(url: "https://a.com", tokenAcceso: token)
            }
        }
    }

    @Test("cerrar sesión borra el token aunque el servidor no conteste")
    func cerrarSinServidor() async throws {
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco-viejo")
        let sesion = sesion(con: llavero)
        buzon.respuesta = .json(respuestaConTokens)
        _ = await sesion.restaurar()

        buzon.fallarLaConexion = true
        await sesion.cerrar()

        #expect(try llavero.tokenRefresco() == nil)
        #expect(await sesion.autenticado == false)
    }
}

/// Contador con cerrojo: el manejador del servidor falso corre en los hilos
/// de URLSession.
private final class Contador: @unchecked Sendable {
    private let cerrojo = NSLock()
    private var veces = 0

    func siguiente() -> Int {
        cerrojo.withLock {
            veces += 1
            return veces
        }
    }
}

@Suite("Entrar con Apple desde el teléfono")
struct PruebasEntrarConApple {
    private let cliente: ClienteApi
    private let buzon: ServidorFalso.Buzon

    init() {
        let (sesionHttp, buzon) = ServidorFalso.sesion()
        self.buzon = buzon
        cliente = ClienteApi(urlBase: "https://api.ejemplo.com", sesionHttp: sesionHttp)
    }

    @Test("manda el token y el nonce a la ruta de Apple, y guarda la sesión")
    func entra() async throws {
        buzon.respuesta = .json(
            """
            {"tokenAcceso": "acceso", "expiraEn": 1, "tokenRefresco": "refresco",
             "usuario": {"id": 7, "email": "persona@privaterelay.appleid.com", "proveedor": "apple"}}
            """
        )
        let llavero = CredencialesEnMemoria()
        let sesion = Sesion(cliente: cliente, credenciales: llavero)

        try await sesion.entrarConApple(identityToken: "el-token-de-apple", nonce: "el-nonce")

        #expect(await sesion.autenticado)
        #expect(await sesion.usuario?.proveedor == "apple")
        #expect(try llavero.tokenRefresco() == "refresco")
        #expect(buzon.ultimaPeticion?.url?.path == "/auth/apple-nativo")

        let enviado =
            (try? JSONSerialization.jsonObject(with: buzon.ultimoCuerpo)) as? [String: Any] ?? [:]
        #expect(enviado["identityToken"] as? String == "el-token-de-apple")
        #expect(enviado["nonce"] as? String == "el-nonce")
    }

    @Test("si el servidor rechaza el token, el fallo llega con su motivo")
    func tokenRechazado() async throws {
        buzon.respuesta = .json(
            #"{"error": "el token no corresponde a esta peticion"}"#, codigo: 400)
        let sesion = Sesion(cliente: cliente, credenciales: CredencialesEnMemoria())

        await #expect(throws: ErrorApi.self) {
            try await sesion.entrarConApple(identityToken: "viejo", nonce: "n")
        }
        #expect(await sesion.autenticado == false)
    }
}
