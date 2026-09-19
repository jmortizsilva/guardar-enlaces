import Dominio
import Foundation
import Testing

@testable import Fontaneria

/// Un par de carpetas nuevas en la carpeta temporal, que se borran al salir.
///
/// Con ficheros de verdad y no con un `FileManager` de mentira: lo que hay
/// que comprobar aquí es precisamente qué pasa en el disco, y un simulacro
/// diría que sí a todo.
private final class CarpetasDePrueba {
    let vieja: URL
    let nueva: URL
    private let raiz: URL

    init() throws {
        raiz = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("mudanza-\(UUID().uuidString)")
        vieja = raiz.appendingPathComponent("privada")
        nueva = raiz.appendingPathComponent("compartida")
        try FileManager.default.createDirectory(at: vieja, withIntermediateDirectories: true)
    }

    deinit {
        try? FileManager.default.removeItem(at: raiz)
    }

    func escribir(_ contenido: String, en fichero: URL) throws {
        try Data(contenido.utf8).write(to: fichero)
    }

    func leer(_ fichero: URL) -> String? {
        try? String(contentsOf: fichero, encoding: .utf8)
    }

    func existe(_ fichero: URL) -> Bool {
        FileManager.default.fileExists(atPath: fichero.path)
    }
}

@Suite("Mudanza de la base de datos a la carpeta compartida")
struct PruebasMudanza {
    @Test("Se lleva la base de datos y crea la carpeta de destino")
    func mudaLoQueHabia() throws {
        let carpetas = try CarpetasDePrueba()
        let origen = carpetas.vieja.appendingPathComponent("guardalo.db")
        let destino = carpetas.nueva.appendingPathComponent("guardalo.db")
        try carpetas.escribir("los enlaces de siempre", en: origen)

        let movida = try AlmacenLocal.mudarACompartida(de: origen, a: destino)

        #expect(movida)
        #expect(carpetas.leer(destino) == "los enlaces de siempre")
        #expect(!carpetas.existe(origen))
    }

    @Test("Se lleva también el diario, o se pierde lo último guardado")
    func mudaLosFicherosDeAlLado() throws {
        let carpetas = try CarpetasDePrueba()
        let origen = carpetas.vieja.appendingPathComponent("guardalo.db")
        let destino = carpetas.nueva.appendingPathComponent("guardalo.db")
        try carpetas.escribir("base", en: origen)
        try carpetas.escribir("lo de ahora mismo", en: origen.appendingToPath("-wal"))
        try carpetas.escribir("índice", en: origen.appendingToPath("-shm"))

        _ = try AlmacenLocal.mudarACompartida(de: origen, a: destino)

        #expect(carpetas.leer(destino.appendingToPath("-wal")) == "lo de ahora mismo")
        #expect(carpetas.leer(destino.appendingToPath("-shm")) == "índice")
    }

    @Test("Sin base de datos vieja no hay nada que mudar")
    func sinOrigenNoHaceNada() throws {
        let carpetas = try CarpetasDePrueba()
        let origen = carpetas.vieja.appendingPathComponent("guardalo.db")
        let destino = carpetas.nueva.appendingPathComponent("guardalo.db")

        #expect(try !AlmacenLocal.mudarACompartida(de: origen, a: destino))
        #expect(!carpetas.existe(destino))
    }

    @Test("Si ya hay base compartida, manda esa: no se pisa")
    func noPisaLaCompartida() throws {
        let carpetas = try CarpetasDePrueba()
        let origen = carpetas.vieja.appendingPathComponent("guardalo.db")
        let destino = carpetas.nueva.appendingPathComponent("guardalo.db")
        try FileManager.default.createDirectory(
            at: carpetas.nueva, withIntermediateDirectories: true)
        try carpetas.escribir("lo que guardó la extensión", en: destino)
        try carpetas.escribir("la vieja", en: origen)

        #expect(try !AlmacenLocal.mudarACompartida(de: origen, a: destino))
        #expect(carpetas.leer(destino) == "lo que guardó la extensión")
        #expect(carpetas.existe(origen))
    }

    @Test("La base mudada se sigue pudiendo abrir y conserva los enlaces")
    func loMudadoSigueSirviendo() throws {
        let carpetas = try CarpetasDePrueba()
        let origen = carpetas.vieja.appendingPathComponent("guardalo.db")
        let destino = carpetas.nueva.appendingPathComponent("guardalo.db")

        let antes = try AlmacenLocal(ruta: origen.path)
        try antes.guardar([
            "e1": Elemento(
                id: "e1",
                url: "https://ejemplo.com",
                titulo: "Un enlace",
                etiquetas: [],
                creadoEn: 1,
                actualizadoEn: 1,
                borrado: false
            )
        ])
        antes.cerrar()

        _ = try AlmacenLocal.mudarACompartida(de: origen, a: destino)

        let despues = try AlmacenLocal(ruta: destino.path)
        #expect(try despues.cargarTodos()["e1"]?.titulo == "Un enlace")
    }

    // El camino del grupo que falta no se puede probar aquí: en macOS,
    // `containerURL(forSecurityApplicationGroupIdentifier:)` devuelve una
    // carpeta para cualquier nombre y encima la crea, mientras que en iOS
    // devuelve nil si el grupo no está en los permisos del objetivo. La
    // primera versión de esta prueba esperaba el error y lo que hizo fue
    // dejarse una carpeta en ~/Library/Group Containers.
}

extension URL {
    /// Pega un sufijo al nombre del fichero (`.db` → `.db-wal`), que no es lo
    /// mismo que añadir un componente de ruta.
    fileprivate func appendingToPath(_ sufijo: String) -> URL {
        URL(fileURLWithPath: path + sufijo)
    }
}
