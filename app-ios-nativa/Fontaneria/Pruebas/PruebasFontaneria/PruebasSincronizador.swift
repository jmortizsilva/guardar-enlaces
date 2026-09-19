import Dominio
import Foundation
import Testing

@testable import Fontaneria

@MainActor
@Suite("Ciclo de sincronización")
struct PruebasSincronizador {
    private let almacen: AlmacenLocal
    private let buzon: ServidorFalso.Buzon
    private let sincronizador: Sincronizador

    init() async throws {
        almacen = try AlmacenLocal(ruta: ":memory:")
        let (sesionHttp, buzon) = ServidorFalso.sesion()
        self.buzon = buzon
        let cliente = ClienteApi(urlBase: "https://api.ejemplo.com", sesionHttp: sesionHttp)

        // Sesión ya iniciada: lo que se prueba aquí es el ciclo, no el login.
        let llavero = CredencialesEnMemoria(tokenInicial: "refresco")
        buzon.respuesta = .json(
            #"{"tokenAcceso": "acceso", "expiraEn": 1, "tokenRefresco": "refresco2"}"#
        )
        let sesion = Sesion(cliente: cliente, credenciales: llavero)
        _ = await sesion.restaurar()

        sincronizador = Sincronizador(almacen: almacen, cliente: cliente, sesion: sesion)
    }

    /// Contesta a cada ruta lo que le digan, y deja ver qué se pidió.
    private func responder(subida: String, bajada: String) {
        buzon.manejador = { peticion in
            peticion.httpMethod == "GET"
                ? .json(bajada)
                : .json(peticion.url?.path == "/sincronizar" ? subida : "{}")
        }
    }

    @Test("sin nada pendiente no se sube nada, pero sí se baja")
    func sinPendientes() async throws {
        responder(subida: "{}", bajada: #"{"elementos": [], "servidorEn": 700}"#)

        try await sincronizador.sincronizar()

        #expect(buzon.rutasPedidas.filter { $0 == "/sincronizar" }.count == 1)
        #expect(try almacen.cursor() == 700)
    }

    @Test("lo pendiente se sube y sale de la cola")
    func subeYVaciaLaCola() async throws {
        try almacen.marcarPendiente(
            Elemento(id: "e1", url: "https://a.com", actualizadoEn: 100)
        )
        responder(
            subida: """
                {"elementos": [{"id": "e1", "url": "https://a.com", "titulo": "Lo puso el servidor",
                 "actualizadoEn": 150}], "rechazados": []}
                """,
            bajada: #"{"elementos": [], "servidorEn": 700}"#
        )

        try await sincronizador.sincronizar()

        #expect(try almacen.cargarPendientes().isEmpty)
        // Manda la versión del servidor, aunque difiera de lo que se envió.
        #expect(try almacen.cargarTodos()["e1"]?.titulo == "Lo puso el servidor")
    }

    @Test("lo rechazado también sale de la cola, o se reenvía para siempre")
    func rechazadosFueraDeLaCola() async throws {
        try almacen.marcarPendiente(Elemento(id: "e1", url: "https://a.com", actualizadoEn: 100))
        responder(
            subida: #"{"elementos": [], "rechazados": [{"id": "e1", "motivo": "no_aplicable"}]}"#,
            bajada: #"{"elementos": [], "servidorEn": 700}"#
        )

        let rechazados = try await sincronizador.sincronizar()

        #expect(rechazados == 1)
        #expect(try almacen.cargarPendientes().isEmpty)
    }

    @Test("lo que baja se guarda y el cursor queda en la hora del servidor")
    func bajaYGuarda() async throws {
        responder(
            subida: "{}",
            bajada: """
                {"elementos": [{"id": "e9", "url": "https://b.com", "actualizadoEn": 300}],
                 "etiquetasDefinidas": [{"id": "t1", "nombre": "ocio", "actualizadoEn": 250}],
                 "servidorEn": 900, "masDisponible": false}
                """
        )

        try await sincronizador.sincronizar()

        #expect(try almacen.cargarTodos()["e9"]?.url == "https://b.com")
        #expect(try almacen.cargarEtiquetasDefinidas()["t1"]?.nombre == "ocio")
        #expect(try almacen.cursor() == 900)
    }

    @Test("con más páginas pendientes repite la bajada hasta terminar")
    func variasPaginas() async throws {
        let vueltas = ContadorDeVueltas()
        buzon.manejador = { peticion in
            guard peticion.httpMethod == "GET" else { return .json("{}") }
            return vueltas.siguiente() == 1
                ? .json(
                    """
                    {"elementos": [{"id": "e1", "url": "https://a.com", "actualizadoEn": 500}],
                     "servidorEn": 0, "masDisponible": true}
                    """
                )
                : .json(
                    """
                    {"elementos": [{"id": "e2", "url": "https://b.com", "actualizadoEn": 600}],
                     "servidorEn": 900, "masDisponible": false}
                    """
                )
        }

        try await sincronizador.sincronizar()

        #expect(try almacen.cargarTodos().count == 2)
        #expect(try almacen.cursor() == 900)
        // La segunda vuelta tiene que pedir desde el último cambio recibido,
        // o volvería a traer la misma página para siempre.
        #expect(buzon.rutasPedidas.filter { $0 == "/sincronizar" }.count == 2)
    }

    @Test("un cambio local sin subir no lo pisa una bajada más antigua")
    func noPisaLoPendiente() async throws {
        let mio = Elemento(id: "e1", url: "https://a.com", titulo: "Mío", actualizadoEn: 800)
        try almacen.marcarPendiente(mio)
        responder(
            subida: #"{"elementos": [], "rechazados": []}"#,
            bajada: """
                {"elementos": [{"id": "e1", "url": "https://a.com", "titulo": "Viejo del servidor",
                 "actualizadoEn": 400}], "servidorEn": 900}
                """
        )

        try await sincronizador.sincronizar()

        #expect(try almacen.cargarTodos()["e1"]?.titulo == "Mío")
    }
}

private final class ContadorDeVueltas: @unchecked Sendable {
    private let cerrojo = NSLock()
    private var veces = 0

    func siguiente() -> Int {
        cerrojo.withLock {
            veces += 1
            return veces
        }
    }
}
