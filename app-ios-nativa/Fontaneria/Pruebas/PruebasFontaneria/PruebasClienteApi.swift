import Dominio
import Foundation
import Testing

@testable import Fontaneria

/// En serie: todas comparten el mismo servidor de mentira, y en paralelo se
/// pisarían las respuestas unas a otras.
@Suite("Cliente del servidor")
struct PruebasClienteApi {
    private let cliente: ClienteApi
    private let buzon: ServidorFalso.Buzon

    init() {
        let (sesionHttp, buzon) = ServidorFalso.sesion()
        self.buzon = buzon
        cliente = ClienteApi(urlBase: "https://api.ejemplo.com", sesionHttp: sesionHttp)
    }

    private var cuerpoEnviado: [String: Any] {
        (try? JSONSerialization.jsonObject(with: buzon.ultimoCuerpo))
            as? [String: Any] ?? [:]
    }

    @Test("la barra final de la dirección no se cuela en las rutas")
    func barraFinal() async throws {
        let (sesionHttp, buzonPropio) = ServidorFalso.sesion()
        let conBarras = ClienteApi(urlBase: "https://api.ejemplo.com///", sesionHttp: sesionHttp)
        buzonPropio.respuesta = .json(
            #"{"tokenAcceso": "a", "expiraEn": 1, "tokenRefresco": "r"}"#
        )

        _ = try await conBarras.canjear(codigoCanje: "c")

        #expect(
            buzonPropio.ultimaPeticion?.url?.absoluteString
                == "https://api.ejemplo.com/auth/canjear"
        )
    }

    @Test("la dirección del login lleva los cuatro parámetros del contrato")
    func direccionDeLogin() throws {
        let url = try #require(
            cliente.urlIniciarLogin(
                proveedor: "google", estado: "abc123", esquema: "guardarenlaces")
        )
        let parametros = try #require(
            URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems)
        let porNombre = Dictionary(uniqueKeysWithValues: parametros.map { ($0.name, $0.value) })

        #expect(url.path == "/auth/iniciar")
        #expect(porNombre["proveedor"] == "google")
        #expect(porNombre["modo"] == "deeplink")
        #expect(porNombre["estado"] == "abc123")
        #expect(porNombre["esquema"] == "guardarenlaces")
    }

    @Test("canjear devuelve los tokens y con quién se ha entrado")
    func canjear() async throws {
        buzon.respuesta = .json(
            """
            {"tokenAcceso": "acceso", "expiraEn": 1735689600000, "tokenRefresco": "refresco",
             "usuario": {"id": 1, "email": "persona@ejemplo.com", "proveedor": "google"}}
            """
        )

        let respuesta = try await cliente.canjear(codigoCanje: "codigo")

        #expect(respuesta.tokenAcceso == "acceso")
        #expect(respuesta.tokenRefresco == "refresco")
        #expect(respuesta.usuario?.email == "persona@ejemplo.com")
        #expect(cuerpoEnviado["codigoCanje"] as? String == "codigo")
    }

    @Test("renovar sin usuario también vale: no todas las respuestas lo traen")
    func renovarSinUsuario() async throws {
        buzon.respuesta = .json(
            #"{"tokenAcceso": "a2", "expiraEn": 2, "tokenRefresco": "r2"}"#
        )

        let respuesta = try await cliente.renovar(tokenRefresco: "r1")

        #expect(respuesta.usuario == nil)
        #expect(respuesta.tokenRefresco == "r2")
    }

    @Test("las llamadas con sesión mandan el token en la cabecera")
    func cabeceraDeAutorizacion() async throws {
        buzon.respuesta = .json(
            #"{"titulo": "T", "descripcion": null, "imagenUrl": null, "tipo": "articulo"}"#
        )

        let metadatos = try await cliente.metadatos(url: "https://a.com", tokenAcceso: "elToken")

        #expect(
            buzon.ultimaPeticion?.value(forHTTPHeaderField: "Authorization")
                == "Bearer elToken"
        )
        #expect(metadatos.comoMetadatos.tipo == .articulo)
    }

    @Test("un 401 se reconoce como sesión caducada")
    func sesionCaducada() async throws {
        buzon.respuesta = .json(#"{"error": "token caducado"}"#, codigo: 401)

        await #expect(throws: ErrorApi.self) {
            _ = try await cliente.metadatos(url: "https://a.com", tokenAcceso: "viejo")
        }

        do {
            _ = try await cliente.metadatos(url: "https://a.com", tokenAcceso: "viejo")
        } catch let fallo as ErrorApi {
            #expect(fallo.esSesionCaducada)
            #expect(fallo.mensaje == "token caducado")
        }
    }

    @Test("sin conexión, el aviso está en castellano y no habla de sockets")
    func sinConexion() async throws {
        buzon.fallarLaConexion = true

        do {
            _ = try await cliente.canjear(codigoCanje: "c")
            Issue.record("tenía que haber fallado")
        } catch let fallo as ErrorApi {
            #expect(fallo.mensaje == "sin conexión con el servidor")
            #expect(fallo.codigo == nil)
        }
    }

    @Test("bajar cambios entiende enlaces y etiquetas en la misma respuesta")
    func bajarCambios() async throws {
        buzon.respuesta = .json(
            """
            {"elementos": [{"id": "e1", "url": "https://a.com", "actualizadoEn": 100}],
             "etiquetasDefinidas": [{"id": "t1", "nombre": "ocio", "actualizadoEn": 90}],
             "servidorEn": 1735600000123, "masDisponible": true}
            """
        )

        let respuesta = try await cliente.bajarCambios(desde: 50, tokenAcceso: "t")

        #expect(respuesta.elementos.map(\.id) == ["e1"])
        #expect(respuesta.etiquetasDefinidas.map(\.nombre) == ["ocio"])
        #expect(respuesta.servidorEn == 1_735_600_000_123)
        #expect(respuesta.masDisponible)
        #expect(buzon.ultimaPeticion?.url?.query?.contains("desde=50") == true)
    }

    @Test("una respuesta sin etiquetas no rompe nada")
    func respuestaSinEtiquetas() async throws {
        buzon.respuesta = .json(#"{"elementos": [], "servidorEn": 5}"#)

        let respuesta = try await cliente.bajarCambios(desde: 0, tokenAcceso: "t")

        #expect(respuesta.etiquetasDefinidas.isEmpty)
        #expect(respuesta.masDisponible == false)
    }

    @Test("al subir solo se manda lo que hay, no listas vacías")
    func subirSoloLoQueHay() async throws {
        buzon.respuesta = .json(#"{"elementos": [], "rechazados": []}"#)

        _ = try await cliente.subirCambios(
            elementos: [Elemento(id: "e1", url: "https://a.com", actualizadoEn: 100)],
            etiquetas: [],
            tokenAcceso: "t"
        )

        #expect(cuerpoEnviado["elementos"] != nil)
        #expect(cuerpoEnviado["etiquetasDefinidas"] == nil)
    }

    @Test("lo rechazado llega con su motivo, para poder sacarlo de la cola")
    func rechazados() async throws {
        buzon.respuesta = .json(
            """
            {"elementos": [], "rechazados": [{"id": "e1", "motivo": "no_aplicable"}],
             "etiquetasRechazadas": [{"id": "t1", "motivo": "sin_nombre"}]}
            """
        )

        let respuesta = try await cliente.subirCambios(
            elementos: [Elemento(id: "e1", url: "https://a.com")],
            etiquetas: [],
            tokenAcceso: "t"
        )

        #expect(respuesta.rechazados == [EntradaRechazada(id: "e1", motivo: "no_aplicable")])
        #expect(respuesta.etiquetasRechazadas.map(\.id) == ["t1"])
    }

    @Test("cerrar sesión aguanta que el servidor no conteste nada")
    func cerrarSesionSinCuerpo() async throws {
        buzon.respuesta = ServidorFalso.Respuesta(codigo: 200, cuerpo: Data())

        try await cliente.cerrarSesion(tokenRefresco: "r")

        #expect(buzon.ultimaPeticion?.url?.path == "/auth/logout")
    }
}
