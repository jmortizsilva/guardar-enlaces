import Foundation

/// Un servidor de mentira que se mete por debajo de `URLSession`, para poder
/// probar el cliente entero (cabeceras, cuerpo, códigos de error) sin red y
/// sin backend levantado.
final class ServidorFalso: URLProtocol {
    struct Respuesta {
        let codigo: Int
        let cuerpo: Data

        static func json(_ texto: String, codigo: Int = 200) -> Respuesta {
            Respuesta(codigo: codigo, cuerpo: Data(texto.utf8))
        }
    }

    /// Lo que va a responder y lo que le han pedido, protegido con un cerrojo
    /// porque `URLSession` llama a esto desde sus propios hilos.
    final class Buzon: @unchecked Sendable {
        private let cerrojo = NSLock()
        private var _respuesta = Respuesta.json("{}")
        private var _manejador: (@Sendable (URLRequest) -> Respuesta)?
        private var _rutasPedidas: [String] = []
        private var _fallarLaConexion = false
        private var _ultimaPeticion: URLRequest?
        private var _ultimoCuerpo = Data()

        var respuesta: Respuesta {
            get { cerrojo.withLock { _respuesta } }
            set { cerrojo.withLock { _respuesta = newValue } }
        }

        /// Para las pruebas que necesitan contestar distinto según la ruta, o
        /// distinto cada vez (renovar y reintentar, por ejemplo).
        var manejador: (@Sendable (URLRequest) -> Respuesta)? {
            get { cerrojo.withLock { _manejador } }
            set { cerrojo.withLock { _manejador = newValue } }
        }

        /// Las rutas pedidas, en orden. Sirve para comprobar que algo se
        /// intentó dos veces, o que no se llamó al servidor en absoluto.
        var rutasPedidas: [String] { cerrojo.withLock { _rutasPedidas } }

        var fallarLaConexion: Bool {
            get { cerrojo.withLock { _fallarLaConexion } }
            set { cerrojo.withLock { _fallarLaConexion = newValue } }
        }

        var ultimaPeticion: URLRequest? { cerrojo.withLock { _ultimaPeticion } }
        var ultimoCuerpo: Data { cerrojo.withLock { _ultimoCuerpo } }

        func anotar(_ peticion: URLRequest, cuerpo: Data) {
            cerrojo.withLock {
                _ultimaPeticion = peticion
                _ultimoCuerpo = cuerpo
                _rutasPedidas.append(peticion.url?.path ?? "")
            }
        }
    }

    /// Un buzón por sesión, no uno compartido: dos grupos de pruebas
    /// corriendo a la vez se pisarían las respuestas, y el fallo saldría
    /// unas veces sí y otras no, que es la peor clase de prueba.
    private static let registro = Registro()

    private static let cabeceraDelBuzon = "X-Buzon-De-Pruebas"

    final class Registro: @unchecked Sendable {
        private let cerrojo = NSLock()
        private var buzones: [String: Buzon] = [:]

        func anotar(_ identificador: String, _ buzon: Buzon) {
            cerrojo.withLock { buzones[identificador] = buzon }
        }

        func buscar(_ identificador: String) -> Buzon? {
            cerrojo.withLock { buzones[identificador] }
        }
    }

    /// Una sesión que pasa por este protocolo en vez de salir a la red, con
    /// su buzón para decirle qué contestar y ver qué le han pedido.
    static func sesion() -> (URLSession, Buzon) {
        let buzon = Buzon()
        let identificador = UUID().uuidString
        registro.anotar(identificador, buzon)

        let configuracion = URLSessionConfiguration.ephemeral
        configuracion.protocolClasses = [ServidorFalso.self]
        configuracion.httpAdditionalHeaders = [cabeceraDelBuzon: identificador]
        return (URLSession(configuration: configuracion), buzon)
    }

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let identificador = request.value(forHTTPHeaderField: Self.cabeceraDelBuzon),
            let buzon = Self.registro.buscar(identificador)
        else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        buzon.anotar(request, cuerpo: Self.cuerpo(de: request))

        if buzon.fallarLaConexion {
            client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet))
            return
        }

        let respuesta = buzon.manejador.map { $0(request) } ?? buzon.respuesta
        let http = HTTPURLResponse(
            url: request.url!,
            statusCode: respuesta.codigo,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: respuesta.cuerpo)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    /// `URLSession` convierte el cuerpo en un flujo antes de llegar aquí, así
    /// que `httpBody` suele venir vacío y hay que leerlo del flujo.
    private static func cuerpo(de peticion: URLRequest) -> Data {
        if let cuerpo = peticion.httpBody {
            return cuerpo
        }
        guard let flujo = peticion.httpBodyStream else {
            return Data()
        }
        flujo.open()
        defer { flujo.close() }
        var datos = Data()
        let tamano = 4096
        var trozo = [UInt8](repeating: 0, count: tamano)
        while flujo.hasBytesAvailable {
            let leidos = flujo.read(&trozo, maxLength: tamano)
            if leidos <= 0 { break }
            datos.append(trozo, count: leidos)
        }
        return datos
    }
}
