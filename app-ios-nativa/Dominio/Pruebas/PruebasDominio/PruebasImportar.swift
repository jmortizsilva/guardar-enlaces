import Foundation
import Testing

@testable import Dominio

/// Estas pruebas NO traen sus propios casos: leen los mismos ficheros y el
/// mismo `esperado.json` que las de Windows y Android, en
/// `pruebas-compartidas/importacion/`. Ese es el único sitio donde se decide
/// qué debe salir de cada fichero.
private let compartidas = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()  // PruebasDominio
    .deletingLastPathComponent()  // Pruebas
    .deletingLastPathComponent()  // Dominio
    .deletingLastPathComponent()  // app-ios-nativa
    .deletingLastPathComponent()  // raíz del repositorio
    .appendingPathComponent("pruebas-compartidas/importacion")

private let ficheros = [
    "marcadores-navegador.html", "pocket.csv", "raindrop.csv", "excel.csv", "enlaces.txt",
]

private func contenido(_ nombre: String) throws -> String {
    try String(contentsOf: compartidas.appendingPathComponent(nombre), encoding: .utf8)
}

private func esperado(_ nombre: String) throws -> [String: Any] {
    let datos = try Data(contentsOf: compartidas.appendingPathComponent("esperado.json"))
    let todo = try #require(JSONSerialization.jsonObject(with: datos) as? [String: Any])
    return try #require(todo[nombre] as? [String: Any])
}

@Suite("Importar, contra los ejemplos compartidos")
struct PruebasImportar {
    @Test("los ficheros compartidos están donde se espera")
    func estanLosFicheros() {
        // Si esto falla, se movió la carpeta y las otras dos apps también se
        // han quedado sin sus casos.
        for nombre in ficheros + ["esperado.json"] {
            #expect(
                FileManager.default.fileExists(
                    atPath: compartidas.appendingPathComponent(nombre).path),
                "falta \(nombre)"
            )
        }
    }

    @Test("sale exactamente lo que dice el contrato", arguments: ficheros)
    func comoDiceElContrato(_ nombre: String) throws {
        let quiero = try esperado(nombre)
        let elementos = try #require(quiero["elementos"] as? [[String: Any]])
        let lectura = Importar.leer(try contenido(nombre))

        #expect(lectura.enlaces.map(\.url) == elementos.map { $0["url"] as? String })
        #expect(lectura.enlaces.map(\.titulo) == elementos.map { $0["titulo"] as? String })
        #expect(
            lectura.enlaces.map(\.etiquetas) == elementos.map { $0["etiquetas"] as? [String] ?? [] }
        )
        #expect(
            lectura.enlaces.map(\.creadoEn)
                == elementos.map { ($0["creadoEn"] as? NSNumber).map { $0.int64Value } }
        )
        #expect(lectura.descartados == quiero["descartados"] as? Int)
    }

    @Test("el formato se reconoce por el contenido, no por el nombre")
    func formatoPorContenido() throws {
        #expect(Importar.detectarFormato(try contenido("marcadores-navegador.html")) == .html)
        #expect(Importar.detectarFormato(try contenido("pocket.csv")) == .csv)
        #expect(Importar.detectarFormato(try contenido("raindrop.csv")) == .csv)
        #expect(Importar.detectarFormato(try contenido("excel.csv")) == .csv)
        #expect(Importar.detectarFormato(try contenido("enlaces.txt")) == .txt)
    }

    @Test("un CSV sin columna de dirección no importa nada")
    func csvSinDirecciones() {
        #expect(Importar.leer("nombre,comentario\nAlgo,Otra cosa\n").enlaces.isEmpty)
    }

    @Test("un fichero vacío no revienta")
    func ficheroVacio() {
        let lectura = Importar.leer("")
        #expect(lectura.enlaces.isEmpty)
        #expect(lectura.descartados == 0)
    }

    @Test("dos direcciones que solo cambian en el seguimiento cuentan como una")
    func repetidosConSeguimiento() {
        let lectura = Importar.leer(
            "https://ejemplo.com/a?utm_source=twitter\nhttps://ejemplo.com/a\n")
        #expect(lectura.enlaces.count == 1)
        #expect(lectura.descartados == 1)
    }
}

@Suite("Importar: las codificaciones que trae cada aplicación")
struct PruebasImportarCodificacion {
    private let csv = "title,url\nCanción española,https://ejemplo.com/a\n"

    @Test(
        "el título llega bien en las tres codificaciones",
        arguments: [String.Encoding.utf8, .windowsCP1252]
    )
    func codificaciones(_ codificacion: String.Encoding) throws {
        let datos = try #require(csv.data(using: codificacion))
        #expect(
            Importar.leer(Importar.texto(de: datos)).enlaces.first?.titulo == "Canción española")
    }

    @Test("con la marca de orden de bytes que pone Excel")
    func conMarcaDeExcel() throws {
        let datos = Data([0xEF, 0xBB, 0xBF]) + (try #require(csv.data(using: .utf8)))
        let lectura = Importar.leer(Importar.texto(de: datos))
        #expect(lectura.formato == .csv)
        #expect(lectura.enlaces.first?.titulo == "Canción española")
    }
}

@Suite("Importar: qué se guarda de lo leído")
struct PruebasImportarPreparar {
    private func lectura() throws -> Importar.Lectura {
        Importar.leer(try contenido("marcadores-navegador.html"))
    }

    @Test("sin nada guardado entran todos")
    func entranTodos() throws {
        let preparado = Importar.preparar(try lectura(), existentes: [], ahora: { 9_000_000 })
        #expect(preparado.importados == 6)
        #expect(preparado.yaEstaban == 0)
    }

    @Test("un enlace que ya tienes no se toca")
    func noSeToca() throws {
        // Lo contrario de guardar a mano, y a propósito: importar toca
        // cientos de golpe, así que pisar lo que pusiste tú no tendría vuelta.
        let mio = Elemento(
            id: "mio", url: "https://ejemplo.com/flan", titulo: "Mi flan de siempre",
            etiquetas: ["pendiente"], creadoEn: 1, actualizadoEn: 1
        )
        let preparado = Importar.preparar(try lectura(), existentes: [mio], ahora: { 9_000_000 })
        #expect(preparado.yaEstaban == 1)
        #expect(!preparado.nuevos.contains { $0.url == mio.url })
    }

    @Test("un enlace que borraste vuelve a entrar")
    func borradoVuelve() throws {
        let borrado = Elemento(
            id: "b", url: "https://ejemplo.com/flan", creadoEn: 1, actualizadoEn: 1
        ).marcadoComoBorrado(ahora: { 2 })
        let preparado = Importar.preparar(
            try lectura(), existentes: [borrado], ahora: { 9_000_000 })
        #expect(preparado.yaEstaban == 0)
    }

    @Test("la fecha del fichero manda sobre la del reloj")
    func fechaDelFichero() throws {
        let preparado = Importar.preparar(try lectura(), existentes: [], ahora: { 9_000_000 })
        let porTitulo = Dictionary(
            uniqueKeysWithValues: preparado.nuevos.compactMap { e in
                e.titulo.map { ($0, e.creadoEn) }
            }
        )
        #expect(porTitulo["Arroz caldoso"] == 1_700_000_300_000)
        #expect(porTitulo["Un marcador sin ADD_DATE"] == 9_000_000)
    }
}
