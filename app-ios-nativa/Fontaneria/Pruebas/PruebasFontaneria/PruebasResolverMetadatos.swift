import Dominio
import Foundation
import Testing

@testable import Fontaneria

@Suite("Quién resuelve los metadatos")
struct PruebasResolverMetadatos {
    private let buzon: ServidorFalso.Buzon
    private let cliente: ClienteApi
    private let sesionHttp: URLSession

    init() {
        let (sesionHttp, buzon) = ServidorFalso.sesion()
        self.sesionHttp = sesionHttp
        self.buzon = buzon
        cliente = ClienteApi(urlBase: "https://api.ejemplo.com", sesionHttp: sesionHttp)
    }

    private func resolvedor(conSesionIniciada: Bool) async -> ResolverMetadatos {
        let llavero = CredencialesEnMemoria(tokenInicial: conSesionIniciada ? "refresco" : nil)
        let sesion = Sesion(cliente: cliente, credenciales: llavero)
        if conSesionIniciada {
            buzon.respuesta = .json(
                #"{"tokenAcceso": "acceso", "expiraEn": 1, "tokenRefresco": "r2"}"#
            )
            _ = await sesion.restaurar()
        }
        return ResolverMetadatos(cliente: cliente, sesion: sesion, sesionHttp: sesionHttp)
    }

    @Test("con cuenta los pide al servidor, que es quien los guarda para los dos clientes")
    func conCuenta() async throws {
        let resolvedor = await resolvedor(conSesionIniciada: true)
        buzon.respuesta = .json(
            #"{"titulo": "Lo dijo el servidor", "descripcion": null, "imagenUrl": null, "tipo": "articulo"}"#
        )

        let metadatos = await resolvedor.resolver(url: "https://a.com")

        #expect(metadatos?.titulo == "Lo dijo el servidor")
        #expect(metadatos?.tipo == .articulo)
        #expect(buzon.rutasPedidas.contains("/metadatos"))
    }

    @Test("sin cuenta descarga la página el propio teléfono")
    func sinCuenta() async throws {
        let resolvedor = await resolvedor(conSesionIniciada: false)
        buzon.respuesta = .json(
            "<html><head><title>Lo leyó el teléfono</title></head></html>"
        )

        let metadatos = await resolvedor.resolver(url: "https://a.com/articulo")

        #expect(metadatos?.titulo == "Lo leyó el teléfono")
        // Nunca pasa por el servidor: sin cuenta no hay a quién preguntar.
        #expect(!buzon.rutasPedidas.contains("/metadatos"))
    }

    @Test("un vídeo de YouTube se resuelve por su oEmbed, no raspando la página")
    func youtube() async throws {
        let resolvedor = await resolvedor(conSesionIniciada: false)
        buzon.respuesta = .json(
            #"{"title": "Un vídeo", "thumbnail_url": "https://i.ytimg.com/a.jpg"}"#
        )

        let metadatos = await resolvedor.resolver(url: "https://youtu.be/abc123")

        #expect(metadatos?.titulo == "Un vídeo")
        #expect(metadatos?.tipo == .video)
        #expect(buzon.ultimaPeticion?.url?.path == "/oembed")
    }

    @Test("si no se puede comprobar, no se inventa nada ni se lanza un error")
    func fallaLaComprobacion() async throws {
        let resolvedor = await resolvedor(conSesionIniciada: false)
        buzon.fallarLaConexion = true

        // Guardar el enlace no puede depender de esto, así que aquí se
        // devuelve que no se sabe, y ya está.
        #expect(await resolvedor.resolver(url: "https://a.com") == nil)
    }

    @Test("una página que responde con error tampoco da metadatos inventados")
    func paginaConError() async throws {
        let resolvedor = await resolvedor(conSesionIniciada: false)
        buzon.respuesta = .json("<html>no autorizado</html>", codigo: 403)

        #expect(await resolvedor.resolver(url: "https://a.com") == nil)
    }
}
