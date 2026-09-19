import Dominio
import Foundation

/// Caché local (SQLite) más la cola de cambios pendientes de subir.
///
/// Aquí solo se persiste; cómo fusionar lo decide `Dominio`, que se prueba
/// sin base de datos. La cola es sencilla a propósito: solo el conjunto de
/// identificadores con un cambio local sin confirmar. El elemento en sí ya
/// está en su tabla, actualizado en el momento del cambio, para que la
/// pantalla lo vea al instante sin esperar a la red.
///
/// Mismo esquema que la app de Windows, tabla por tabla y columna por
/// columna: son dos clientes del mismo contrato, y cuando algo falle en uno
/// hay que poder mirar el otro.
///
/// La clase no es `Sendable` a propósito: se crea y se usa desde un solo
/// sitio (en la app, la interfaz). SQLite local tarda microsegundos, así que
/// no compensa complicarlo con un actor y volver asíncrona cada lectura.
public final class AlmacenLocal {
    private let bd: BaseDatos

    /// Ruta en disco, o `":memory:"` para una base de datos que vive solo
    /// mientras dure el objeto (lo que usan las pruebas).
    public init(ruta: String) throws {
        bd = try BaseDatos(ruta: ruta)
        try prepararParaDosProcesos()
        try crearEsquema()
    }

    /// La app y la extensión de compartir abren el mismo fichero desde
    /// procesos distintos, y sin esto se pisan.
    ///
    /// Con el diario de siempre, quien escribe bloquea la base entera y el
    /// otro recibe «database is locked» en el acto: el enlace compartido se
    /// perdería justo cuando la app está delante. `WAL` deja que uno escriba
    /// mientras el otro lee, y la espera convierte el choque que queda en
    /// unos milisegundos de cola en vez de un fallo.
    private func prepararParaDosProcesos() throws {
        try bd.ejecutar(
            """
            PRAGMA journal_mode = WAL;
            PRAGMA busy_timeout = 5000;
            """
        )
    }

    private func crearEsquema() throws {
        try bd.ejecutar(
            """
            CREATE TABLE IF NOT EXISTS elementos (
                id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                titulo TEXT,
                descripcion TEXT,
                imagen_url TEXT,
                tipo TEXT NOT NULL DEFAULT 'enlace',
                etiquetas TEXT NOT NULL DEFAULT '[]',
                creado_en INTEGER NOT NULL,
                actualizado_en INTEGER NOT NULL,
                borrado INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS outbox (
                id TEXT PRIMARY KEY
            );
            CREATE TABLE IF NOT EXISTS etiquetas_definidas (
                id TEXT PRIMARY KEY,
                nombre TEXT NOT NULL,
                creado_en INTEGER NOT NULL,
                actualizado_en INTEGER NOT NULL,
                borrado INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS outbox_etiquetas (
                id TEXT PRIMARY KEY
            );
            CREATE TABLE IF NOT EXISTS estado_sincronizacion (
                clave TEXT PRIMARY KEY,
                valor INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS estado_texto (
                clave TEXT PRIMARY KEY,
                valor TEXT NOT NULL
            );
            """
        )
    }

    public func cerrar() {
        bd.cerrar()
    }

    // MARK: - Cuenta a la que pertenece lo guardado

    /// Borra la caché entera: enlaces, etiquetas, colas, cursor y dueño. Se
    /// llama al cerrar sesión y al entrar alguien distinto.
    public func vaciar() throws {
        try bd.ejecutar(
            """
            DELETE FROM elementos;
            DELETE FROM outbox;
            DELETE FROM etiquetas_definidas;
            DELETE FROM outbox_etiquetas;
            DELETE FROM estado_sincronizacion;
            DELETE FROM estado_texto;
            """
        )
    }

    public func duenoActual() throws -> String? {
        try bd.consultar("SELECT valor FROM estado_texto WHERE clave = 'dueno'") { $0.texto(0) }
            .first ?? nil
    }

    public func fijarDueno(_ valor: String) throws {
        try bd.ejecutar(
            """
            INSERT INTO estado_texto (clave, valor) VALUES ('dueno', ?)
            ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor
            """,
            [.texto(valor)]
        )
    }

    /// Enlaces visibles, sin lápidas: lo que cuenta para preguntarle al
    /// usuario si quiere quedárselos al entrar en una cuenta.
    public func contarElementos() throws -> Int {
        let cuentas = try bd.consultar("SELECT COUNT(*) FROM elementos WHERE borrado = 0") {
            Int($0.entero(0))
        }
        return cuentas.first ?? 0
    }

    /// Adopta lo que hay en el teléfono para la cuenta en la que se acaba de
    /// entrar: identificadores nuevos y a la cola de subida.
    ///
    /// Los identificadores nuevos no son un capricho. Si estos enlaces venían
    /// de otra cuenta, el servidor ya tiene esos mismos a nombre de su dueño
    /// anterior y rechaza el cambio, porque no puede dejar que una cuenta
    /// pise elementos de otra. Antes de darse cuenta de esto, los enlaces se
    /// quedaban en la cola reintentándose para siempre, en silencio.
    ///
    /// Las lápidas se tiran en vez de subirse: son el rastro de un borrado
    /// que la OTRA cuenta ya conoce, y en esta no significan nada.
    public func adoptarConIdsNuevos(generarId: GeneradorId = generarIdUnico) throws {
        try bd.ejecutar("DELETE FROM elementos WHERE borrado = 1")
        try bd.ejecutar("DELETE FROM etiquetas_definidas WHERE borrado = 1")
        // Las colas apuntan a los identificadores viejos: se vacían antes de
        // renumerar, o quedarían señalando a filas que ya no existen.
        try bd.ejecutar("DELETE FROM outbox")
        try bd.ejecutar("DELETE FROM outbox_etiquetas")

        for id in try bd.consultar("SELECT id FROM elementos", leer: { $0.textoObligatorio(0) }) {
            try bd.ejecutar(
                "UPDATE elementos SET id = ? WHERE id = ?",
                [.texto(generarId()), .texto(id)]
            )
        }
        let etiquetas = try bd.consultar("SELECT id FROM etiquetas_definidas") {
            $0.textoObligatorio(0)
        }
        for id in etiquetas {
            try bd.ejecutar(
                "UPDATE etiquetas_definidas SET id = ? WHERE id = ?",
                [.texto(generarId()), .texto(id)]
            )
        }

        try bd.ejecutar("INSERT INTO outbox (id) SELECT id FROM elementos")
        try bd.ejecutar("INSERT INTO outbox_etiquetas (id) SELECT id FROM etiquetas_definidas")
    }

    // MARK: - Cursor de sincronización (el último "servidorEn" recibido)

    public func cursor() throws -> MarcaDeTiempo {
        let valores = try bd.consultar(
            "SELECT valor FROM estado_sincronizacion WHERE clave = 'cursor'"
        ) { $0.entero(0) }
        return valores.first ?? 0
    }

    public func fijarCursor(_ valor: MarcaDeTiempo) throws {
        try bd.ejecutar(
            """
            INSERT INTO estado_sincronizacion (clave, valor) VALUES ('cursor', ?)
            ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor
            """,
            [.entero(valor)]
        )
    }

    // MARK: - Enlaces

    public func cargarTodos() throws -> Cache {
        let elementos = try bd.consultar("SELECT * FROM elementos", leer: Self.elementoDeFila)
        return Dictionary(uniqueKeysWithValues: elementos.map { ($0.id, $0) })
    }

    public func cargarPendientes() throws -> Cache {
        let elementos = try bd.consultar(
            "SELECT e.* FROM elementos e JOIN outbox o ON o.id = e.id",
            leer: Self.elementoDeFila
        )
        return Dictionary(uniqueKeysWithValues: elementos.map { ($0.id, $0) })
    }

    /// Guarda el resultado de una fusión. No toca la cola de pendientes: eso
    /// lo decide quien orquesta la sincronización.
    public func guardar(_ cache: Cache) throws {
        for elemento in cache.values {
            try upsert(elemento)
        }
    }

    /// Un cambio local (alta, edición o baja): se ve al instante y queda
    /// encolado para la próxima subida.
    public func marcarPendiente(_ elemento: Elemento) throws {
        try upsert(elemento)
        try bd.ejecutar("INSERT OR IGNORE INTO outbox (id) VALUES (?)", [.texto(elemento.id)])
    }

    public func limpiarPendientes(_ ids: [String]) throws {
        for id in ids {
            try bd.ejecutar("DELETE FROM outbox WHERE id = ?", [.texto(id)])
        }
    }

    private func upsert(_ elemento: Elemento) throws {
        try bd.ejecutar(
            """
            INSERT INTO elementos
                (id, url, titulo, descripcion, imagen_url, tipo, etiquetas,
                 creado_en, actualizado_en, borrado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                url = excluded.url, titulo = excluded.titulo,
                descripcion = excluded.descripcion, imagen_url = excluded.imagen_url,
                tipo = excluded.tipo, etiquetas = excluded.etiquetas,
                creado_en = excluded.creado_en, actualizado_en = excluded.actualizado_en,
                borrado = excluded.borrado
            """,
            [
                .texto(elemento.id),
                .texto(elemento.url),
                .init(elemento.titulo),
                .init(elemento.descripcion),
                .init(elemento.imagenUrl),
                .texto(elemento.tipo.rawValue),
                .texto(Self.etiquetasAJson(elemento.etiquetas)),
                .entero(elemento.creadoEn),
                .entero(elemento.actualizadoEn),
                .init(elemento.borrado),
            ]
        )
    }

    // MARK: - Etiquetas reservadas

    public func cargarEtiquetasDefinidas() throws -> CacheEtiquetas {
        let etiquetas = try bd.consultar(
            "SELECT * FROM etiquetas_definidas", leer: Self.etiquetaDeFila)
        return Dictionary(uniqueKeysWithValues: etiquetas.map { ($0.id, $0) })
    }

    public func cargarEtiquetasPendientes() throws -> CacheEtiquetas {
        let etiquetas = try bd.consultar(
            "SELECT e.* FROM etiquetas_definidas e JOIN outbox_etiquetas o ON o.id = e.id",
            leer: Self.etiquetaDeFila
        )
        return Dictionary(uniqueKeysWithValues: etiquetas.map { ($0.id, $0) })
    }

    public func guardarEtiquetasDefinidas(_ cache: CacheEtiquetas) throws {
        for etiqueta in cache.values {
            try upsert(etiqueta)
        }
    }

    public func marcarEtiquetaPendiente(_ etiqueta: EtiquetaDefinida) throws {
        try upsert(etiqueta)
        try bd.ejecutar(
            "INSERT OR IGNORE INTO outbox_etiquetas (id) VALUES (?)",
            [.texto(etiqueta.id)]
        )
    }

    public func limpiarEtiquetasPendientes(_ ids: [String]) throws {
        for id in ids {
            try bd.ejecutar("DELETE FROM outbox_etiquetas WHERE id = ?", [.texto(id)])
        }
    }

    private func upsert(_ etiqueta: EtiquetaDefinida) throws {
        try bd.ejecutar(
            """
            INSERT INTO etiquetas_definidas (id, nombre, creado_en, actualizado_en, borrado)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nombre = excluded.nombre, creado_en = excluded.creado_en,
                actualizado_en = excluded.actualizado_en, borrado = excluded.borrado
            """,
            [
                .texto(etiqueta.id),
                .texto(etiqueta.nombre),
                .entero(etiqueta.creadoEn),
                .entero(etiqueta.actualizadoEn),
                .init(etiqueta.borrado),
            ]
        )
    }

    // MARK: - Filas a objetos

    private static func elementoDeFila(_ fila: BaseDatos.Fila) -> Elemento {
        Elemento(
            id: fila.textoObligatorio(0),
            url: fila.textoObligatorio(1),
            titulo: fila.texto(2),
            descripcion: fila.texto(3),
            imagenUrl: fila.texto(4),
            tipo: TipoElemento(rawValue: fila.textoObligatorio(5)) ?? .enlace,
            etiquetas: etiquetasDesdeJson(fila.textoObligatorio(6)),
            creadoEn: fila.entero(7),
            actualizadoEn: fila.entero(8),
            borrado: fila.bandera(9)
        )
    }

    private static func etiquetaDeFila(_ fila: BaseDatos.Fila) -> EtiquetaDefinida {
        EtiquetaDefinida(
            id: fila.textoObligatorio(0),
            nombre: fila.textoObligatorio(1),
            creadoEn: fila.entero(2),
            actualizadoEn: fila.entero(3),
            borrado: fila.bandera(4)
        )
    }

    /// Las etiquetas van en una columna de texto como lista JSON, igual que
    /// en los otros dos clientes: es el mismo fichero de base de datos leído
    /// por gente distinta, así que el formato no se cambia por gusto.
    private static func etiquetasAJson(_ etiquetas: [String]) -> String {
        guard let datos = try? JSONEncoder().encode(etiquetas),
            let texto = String(data: datos, encoding: .utf8)
        else {
            return "[]"
        }
        return texto
    }

    private static func etiquetasDesdeJson(_ texto: String) -> [String] {
        (try? JSONDecoder().decode([String].self, from: Data(texto.utf8))) ?? []
    }
}

extension AlmacenLocal {
    /// Dónde vive la base de datos en el teléfono.
    ///
    /// En «Application Support» y no en «Documents»: lo de Documents se
    /// sincroniza con iCloud y sale en la app Archivos si algún día se activa
    /// eso, y esto es una caché interna, no un documento de nadie.
    public static func rutaPorDefecto(nombre: String = "guardalo.db") throws -> String {
        let gestor = FileManager.default
        let carpeta = try gestor.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        // En iOS, «Application Support» puede no existir todavía la primera
        // vez, y SQLite no crea carpetas: falla al abrir y no se entiende por
        // qué.
        try gestor.createDirectory(at: carpeta, withIntermediateDirectories: true)
        return carpeta.appendingPathComponent(nombre).path
    }

    public enum FalloDeCarpeta: Error, CustomStringConvertible {
        case grupoNoDisponible(String)

        public var description: String {
            switch self {
            case .grupoNoDisponible(let grupo):
                "el grupo \(grupo) no está en los permisos de este objetivo"
            }
        }
    }

    /// Los ficheros que SQLite puede tener junto a la base de datos. Con
    /// `WAL` son dos más, y mudar la base sin ellos deja fuera lo último
    /// escrito.
    private static let sufijosSqlite = ["", "-wal", "-shm", "-journal"]

    /// La carpeta que comparten la app y la extensión de compartir.
    ///
    /// Una extensión es otro proceso con su propio sandbox: la carpeta de la
    /// app no la ve, así que la base de datos tiene que vivir aquí o no hay
    /// nada que compartir.
    public static func carpetaCompartida(
        grupo: String,
        gestor: FileManager = .default
    ) throws -> URL {
        guard let carpeta = gestor.containerURL(forSecurityApplicationGroupIdentifier: grupo)
        else {
            throw FalloDeCarpeta.grupoNoDisponible(grupo)
        }
        return carpeta
    }

    /// Trae la base de datos de la carpeta privada de la app a la compartida.
    /// Dice si movió algo.
    ///
    /// Se llama al arrancar la app, una sola vez en la vida de cada
    /// instalación: quien ya tenía enlaces guardados no los pierde al
    /// aparecer la extensión.
    ///
    /// No pisa nada: si en el destino ya hay una base de datos, esa manda. La
    /// de origen es la vieja por definición, y machacar la compartida
    /// borraría lo que la extensión hubiera guardado entretanto.
    @discardableResult
    public static func mudarACompartida(
        de origen: URL,
        a destino: URL,
        gestor: FileManager = .default
    ) throws -> Bool {
        guard gestor.fileExists(atPath: origen.path) else { return false }
        guard !gestor.fileExists(atPath: destino.path) else { return false }

        try gestor.createDirectory(
            at: destino.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        for sufijo in sufijosSqlite {
            let desde = URL(fileURLWithPath: origen.path + sufijo)
            guard gestor.fileExists(atPath: desde.path) else { continue }
            try gestor.moveItem(at: desde, to: URL(fileURLWithPath: destino.path + sufijo))
        }
        return true
    }

    /// Deja la base de datos legible aunque el teléfono esté bloqueado, a
    /// partir del primer desbloqueo tras encenderlo.
    ///
    /// Con la protección que iOS pone por defecto, compartir un enlace con la
    /// pantalla bloqueada falla al abrir la base: la extensión arranca, no
    /// puede leer el fichero y el enlace se pierde sin que nadie se entere.
    public static func aflojarProteccion(
        de fichero: URL,
        gestor: FileManager = .default
    ) throws {
        for sufijo in sufijosSqlite {
            let ruta = fichero.path + sufijo
            guard gestor.fileExists(atPath: ruta) else { continue }
            try gestor.setAttributes(
                [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                ofItemAtPath: ruta
            )
        }
    }
}
