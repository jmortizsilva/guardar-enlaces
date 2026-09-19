import Dominio
import Fontaneria
import Observation
import SwiftUI

/// Cablea almacén, cliente, sesión y sincronizador, y se los da a las
/// pantallas. Es el equivalente de `ProveedorApp.tsx` en la app de Expo.
///
/// Vive en el hilo principal, como el almacén que comparte con la interfaz.
@MainActor
@Observable
final class ModeloApp {
    /// Si ya se ha visto la pantalla de bienvenida alguna vez.
    static let claveBienvenidaVista = "bienvenidaVista"

    /// La bienvenida solo se enseña al estrenar la app, y nunca si ya hay
    /// sesión: quien vuelve a entrar no necesita que le expliquen la app.
    var tocaEnsenarBienvenida: Bool {
        !arrancando && !autenticado
            && !UserDefaults.standard.bool(forKey: Self.claveBienvenidaVista)
    }

    /// Los enlaces no borrados, más recientes primero.
    private(set) var elementos: [Elemento] = []
    private(set) var sincronizando = false
    private(set) var autenticado = false
    private(set) var usuario: UsuarioApi?
    /// Mientras se intenta recuperar una sesión guardada no se sabe aún si
    /// esto va a ir en local o con cuenta.
    private(set) var arrancando = true

    private let almacen: AlmacenLocal
    private let cliente: ClienteApi
    private let sesion: Sesion
    private let sincronizador: Sincronizador
    private let resolvedor: ResolverMetadatos
    private let iniciador = IniciadorDeSesion()
    private let iniciadorApple = IniciadorDeSesionApple()
    /// Para no sincronizar dos veces seguidas al alternar entre aplicaciones.
    private var ultimaSincronizacion: MarcaDeTiempo = 0

    init() throws {
        almacen = try AlmacenLocal(ruta: try AlmacenLocal.rutaPorDefecto())
        cliente = ClienteApi(urlBase: Configuracion.urlApi)
        sesion = Sesion(cliente: cliente, credenciales: CredencialesKeychain())
        sincronizador = Sincronizador(almacen: almacen, cliente: cliente, sesion: sesion)
        resolvedor = ResolverMetadatos(cliente: cliente, sesion: sesion)
    }

    /// Solo para las vistas previas de Xcode y las pruebas de interfaz.
    init(enMemoriaCon elementos: [Elemento]) throws {
        almacen = try AlmacenLocal(ruta: ":memory:")
        cliente = ClienteApi(urlBase: "https://api.ejemplo.com")
        sesion = Sesion(cliente: cliente, credenciales: CredencialesKeychain())
        sincronizador = Sincronizador(almacen: almacen, cliente: cliente, sesion: sesion)
        // Sin red: las pruebas de interfaz no pueden depender de que
        // swift.org conteste, ni ponerse a descargar páginas de verdad cada
        // vez que se ejecutan. Con este tiempo de espera, cualquier petición
        // falla al instante y la comprobación siempre devuelve «no se sabe»,
        // que es justo el camino que interesa ejercitar.
        let sinRed = URLSessionConfiguration.ephemeral
        sinRed.timeoutIntervalForRequest = 0.001
        sinRed.timeoutIntervalForResource = 0.001
        resolvedor = ResolverMetadatos(
            cliente: cliente,
            sesion: sesion,
            sesionHttp: URLSession(configuration: sinRed)
        )
        for elemento in elementos {
            try almacen.guardar([elemento.id: elemento])
        }
        // Las pruebas de interfaz empiezan en la lista, no en la bienvenida: sin
        // esto, la hoja de bienvenida tapaba la app entera y no encontraban nada.
        // Para probar la bienvenida se lanza con `-ensenar-bienvenida`.
        if !ProcessInfo.processInfo.arguments.contains("-ensenar-bienvenida") {
            UserDefaults.standard.set(true, forKey: Self.claveBienvenidaVista)
        }
        arrancando = false
        refrescar()
    }

    // MARK: - Arranque

    func arrancar() async {
        // La lista se lee siempre: sin cuenta es lo único que hay.
        refrescar()
        autenticado = await sesion.restaurar()
        usuario = await sesion.usuario
        arrancando = false
        if autenticado {
            await sincronizarEnSilencio()
        }
    }

    /// Volver a la app trae lo que se haya guardado en el ordenador mientras tanto.
    /// Sin esto solo se enteraba al arrastrar la lista, y «lo guardé en el
    /// otro sitio y aquí no está» es de las cosas que más desconfianza dan.
    func alVolverAPrimerPlano() async {
        guard Sincronizacion.tocaSincronizar(ultima: ultimaSincronizacion, ahora: relojDelSistema())
        else {
            return
        }
        await sincronizarEnSilencio()
    }

    /// Las que ya lleva algún enlace más las reservadas, que existen aunque
    /// todavía no las lleve ninguno.
    var etiquetasDisponibles: [String] {
        let enUso = Biblioteca.etiquetasEnUso(elementos)
        let reservadas =
            (try? almacen.cargarEtiquetasDefinidas())
            .map(Sincronizacion.etiquetasReservadasVisibles)?
            .map(\.nombre) ?? []
        return Set(enUso + reservadas).sorted { $0.localizedCompare($1) == .orderedAscending }
    }

    private func refrescar() {
        elementos = (try? almacen.cargarTodos()).map(Sincronizacion.elementosVisibles) ?? []
    }

    // MARK: - Acciones sobre la lista

    func eliminar(_ elemento: Elemento) {
        try? almacen.marcarPendiente(elemento.marcadoComoBorrado())
        refrescar()
        Anuncios.importante(Textos.eliminado(titulo: Presentacion.titulo(de: elemento)))
        Task { await sincronizarEnSilencio() }
    }

    func cambiarEtiquetas(de elemento: Elemento, a etiquetas: [String]) {
        try? almacen.marcarPendiente(elemento.conEtiquetas(etiquetas))
        refrescar()
        Anuncios.importante(Textos.etiquetasGuardadas(etiquetas))
        Task { await sincronizarEnSilencio() }
    }

    // MARK: - La cuenta

    /// Abre el inicio de sesión de Google y, si sale bien, resuelve qué hacer
    /// con los enlaces que ya hubiera en el teléfono.
    ///
    /// `decidirImportacion` solo se llama si hay algo que decidir: enlaces de
    /// otra cuenta, o guardados sin cuenta. Entrar en la cuenta de siempre no
    /// pregunta nada, que es el caso normal.
    func iniciarSesionConGoogle(
        decidirImportacion: (EnlacesEnElTelefono) async -> Bool
    ) async -> Login.Resultado {
        guard
            let url = cliente.urlIniciarLogin(
                proveedor: "google",
                estado: Login.generarEstado(),
                esquema: IniciadorDeSesion.esquema
            )
        else {
            return .error(mensaje: Login.mensajeDeError(motivo: ""))
        }

        let resultado = await iniciador.pedirCodigoDeCanje(urlAutorizacion: url)
        guard case .exito(let codigo) = resultado else {
            return resultado
        }

        do {
            try await sesion.entrar(conCodigoDeCanje: codigo)
        } catch let fallo as ErrorApi {
            return .error(mensaje: fallo.mensaje)
        } catch {
            return .error(mensaje: Login.mensajeDeError(motivo: ""))
        }

        await asentarLaCuenta(decidirImportacion: decidirImportacion)
        autenticado = true
        usuario = await sesion.usuario
        refrescar()
        await sincronizarEnSilencio()
        return resultado
    }

    /// Entra con Apple sin salir de la app: el sistema pide la identidad y el
    /// token que devuelve se canjea en el servidor.
    func iniciarSesionConApple(
        decidirImportacion: (EnlacesEnElTelefono) async -> Bool
    ) async -> Login.Resultado {
        let nonce = Login.nonceParaApple()
        let respuesta = await iniciadorApple.pedirIdentidad(resumenDelNonce: nonce.resumen)

        let token: String
        switch respuesta {
        case .exito(let identityToken):
            token = identityToken
        case .cancelado:
            return .cancelado
        case .error(let mensaje):
            return .error(mensaje: mensaje)
        }

        do {
            try await sesion.entrarConApple(identityToken: token, nonce: nonce.enClaro)
        } catch let fallo as ErrorApi {
            return .error(mensaje: fallo.mensaje)
        } catch {
            return .error(mensaje: Login.mensajeDeError(motivo: ""))
        }

        await asentarLaCuenta(decidirImportacion: decidirImportacion)
        autenticado = true
        usuario = await sesion.usuario
        refrescar()
        await sincronizarEnSilencio()
        return .exito(codigoDeCanje: "")
    }

    /// Sin correo no se toca nada: no habría con qué comparar y, ante la
    /// duda, ni se tira ni se importa nada.
    private func asentarLaCuenta(decidirImportacion: (EnlacesEnElTelefono) async -> Bool) async {
        guard let email = await sesion.usuario?.email else {
            return
        }
        let dueno = AsentarCuenta.identidadDueno(urlServidor: Configuracion.urlApi, email: email)
        let paso = AsentarCuenta.alEntrar(
            duenoAnterior: try? almacen.duenoActual(),
            dueno: dueno,
            cuantosElementos: (try? almacen.contarElementos()) ?? 0
        )

        switch paso {
        case .noHacerNada:
            return
        case .asentar(let asiento):
            aplicar(asiento)
        case .preguntar(let enlaces):
            aplicar(AsentarCuenta.asiento(segunRespuesta: await decidirImportacion(enlaces)))
        }
        // El cursor era del OTRO servidor: si no se pone a cero, la primera
        // bajada pide «lo cambiado desde» una fecha que aquí no significa
        // nada, y se salta todo lo anterior a ella.
        try? almacen.fijarCursor(0)
        try? almacen.fijarDueno(dueno)
    }

    private func aplicar(_ asiento: AsientoDeCuenta) {
        switch asiento {
        case .adoptarLoQueHay:
            try? almacen.adoptarConIdsNuevos()
        case .empezarDeCero:
            try? almacen.vaciar()
        }
    }

    /// Los enlaces se quedan en el teléfono y la app sigue funcionando en
    /// local. El dueño NO se borra a propósito: si mañana entra otra cuenta,
    /// hay que saber que esto era de alguien y preguntar antes de mezclarlo.
    func cerrarSesion() async {
        await sesion.cerrar()
        autenticado = false
        usuario = nil
        refrescar()
    }

    // MARK: - Añadir un enlace

    /// El enlace ya guardado con esa misma URL, si lo hay. Se consulta
    /// mientras se escribe, para poder avisar antes de guardar y no después.
    func repetido(para url: String) -> Elemento? {
        Duplicados.buscar(en: elementos, url: url)
    }

    /// Título y descripción de una URL. Nunca lanza: guardar el enlace no
    /// puede depender de que esto salga bien.
    func comprobar(url: String) async -> MetadatosExtraidos? {
        await resolvedor.resolver(url: url)
    }

    /// Guarda la URL, o actualiza el enlace que ya la tenía.
    ///
    /// `metadatos` puede venir vacío, y no pasa nada: se guarda igual, solo
    /// que sin título, y se dice en voz alta. Comprobar nunca fue lo
    /// importante; guardar el enlace, sí.
    func guardarEnlace(url: String, etiquetas: [String], metadatos: MetadatosExtraidos?) {
        if let repetido = repetido(para: url) {
            let actualizado = repetido.actualizado(con: metadatos, etiquetasNuevas: etiquetas)
            try? almacen.marcarPendiente(actualizado)
            refrescar()
            Anuncios.importante(Textos.actualizado(titulo: Presentacion.titulo(de: actualizado)))
        } else {
            let nuevo = nuevoElementoLocal(
                DatosElementoNuevo(
                    url: url,
                    titulo: metadatos?.titulo,
                    descripcion: metadatos?.descripcion,
                    imagenUrl: metadatos?.imagenUrl,
                    tipo: metadatos?.tipo ?? .enlace,
                    etiquetas: etiquetas
                )
            )
            try? almacen.marcarPendiente(nuevo)
            refrescar()
            Anuncios.importante(
                metadatos == nil
                    ? Textos.guardadoSinComprobar
                    : Textos.guardado(titulo: Presentacion.titulo(de: nuevo))
            )
        }
        Task { await sincronizarEnSilencio() }
    }

    // MARK: - Gestionar las etiquetas de toda la biblioteca

    /// Las etiquetas con cuántos enlaces lleva cada una. Incluye las
    /// reservadas, que existen aunque todavía no las lleve ninguno.
    var etiquetasConRecuento: [(nombre: String, enlaces: Int)] {
        let recuento = Biblioteca.recuentoPorEtiqueta(elementos)
        return etiquetasDisponibles.map { ($0, recuento[$0] ?? 0) }
    }

    /// Crea una etiqueta que todavía no lleva ningún enlace, para tenerla
    /// lista y poder asignarla luego desde cualquiera de los dos clientes.
    func crearEtiqueta(_ nombre: String) {
        let limpio = nombre.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !limpio.isEmpty, !etiquetasDisponibles.contains(limpio) else {
            return
        }
        try? almacen.marcarEtiquetaPendiente(nuevaEtiquetaDefinida(nombre: limpio))
        refrescar()
        Anuncios.importante(Textos.etiquetaAnadida(limpio))
        Task { await sincronizarEnSilencio() }
    }

    /// Cambia el nombre en todos los enlaces que la llevan, y también en la
    /// etiqueta reservada si existía. Las dos cosas, o el nombre viejo
    /// reaparecería en el otro cliente.
    func renombrarEtiqueta(_ vieja: String, a nueva: String) {
        let limpio = nueva.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !limpio.isEmpty, limpio != vieja else {
            return
        }
        let cambiados = Biblioteca.renombrarEtiqueta(en: elementos, vieja: vieja, nueva: limpio)
        for elemento in cambiados {
            try? almacen.marcarPendiente(elemento)
        }
        if let reservada = etiquetaReservada(conNombre: vieja) {
            try? almacen.marcarEtiquetaPendiente(reservada.renombrada(limpio))
        }
        refrescar()
        Anuncios.importante(
            Textos.etiquetaRenombrada(de: vieja, a: limpio, enlaces: cambiados.count)
        )
        Task { await sincronizarEnSilencio() }
    }

    func eliminarEtiqueta(_ nombre: String) {
        let cambiados = Biblioteca.quitarEtiqueta(en: elementos, etiqueta: nombre)
        for elemento in cambiados {
            try? almacen.marcarPendiente(elemento)
        }
        if let reservada = etiquetaReservada(conNombre: nombre) {
            try? almacen.marcarEtiquetaPendiente(reservada.marcadaComoBorrada())
        }
        refrescar()
        Anuncios.importante(Textos.etiquetaEliminada(nombre, enlaces: cambiados.count))
        Task { await sincronizarEnSilencio() }
    }

    private func etiquetaReservada(conNombre nombre: String) -> EtiquetaDefinida? {
        guard let cache = try? almacen.cargarEtiquetasDefinidas() else {
            return nil
        }
        return Sincronizacion.etiquetasReservadasVisibles(cache).first { $0.nombre == nombre }
    }

    // MARK: - Sincronizar

    /// La que pide el usuario arrastrando la lista: dice cómo ha acabado,
    /// igual que la app de Windows al pulsar F5.
    func sincronizarAMano() async {
        guard autenticado else {
            return
        }
        let antes = Set(elementos.map(\.id))
        do {
            try await ejecutarSincronizacion()
            let nuevos = Set(elementos.map(\.id)).subtracting(antes).count
            Anuncios.importante(Textos.sincronizacionTerminada(enlacesNuevos: nuevos))
        } catch let fallo as ErrorApi {
            Anuncios.importante(Textos.sincronizacionFallida(causa: fallo.mensaje))
        } catch {
            Anuncios.importante(Textos.sincronizacionFallida(causa: "\(error)"))
        }
    }

    /// La automática: se calla pase lo que pase. El cambio ya está en la cola
    /// local y se reintenta en la siguiente; avisar de cada fallo de red sería
    /// ruido constante y no hay nada que decidir al oírlo.
    func sincronizarEnSilencio() async {
        guard autenticado else {
            return
        }
        try? await ejecutarSincronizacion()
    }

    private func ejecutarSincronizacion() async throws {
        ultimaSincronizacion = relojDelSistema()
        sincronizando = true
        defer { sincronizando = false }
        try await sincronizador.sincronizar()
        refrescar()
    }
}
