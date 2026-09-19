import Foundation
import Testing

@testable import Dominio

@Suite("Normalizar una URL para compararla")
struct PruebasNormalizarUrl {
    @Test("http y https son la misma página")
    func esquemaDaIgual() {
        #expect(Duplicados.esLaMisma("http://ejemplo.com/a", "https://ejemplo.com/a"))
    }

    @Test("el «www», la barra final y el ancla no cuentan")
    func adornosNoCuentan() {
        #expect(Duplicados.esLaMisma("https://www.ejemplo.com/a/", "https://ejemplo.com/a#seccion"))
    }

    @Test("los parámetros de seguimiento se descartan")
    func seguimientoFuera() {
        #expect(
            Duplicados.esLaMisma(
                "https://ejemplo.com/a?utm_source=boletin&fbclid=xyz",
                "https://ejemplo.com/a"
            )
        )
    }

    @Test("pero los parámetros que identifican el contenido sí cuentan")
    func parametrosDeContenido() {
        #expect(
            !Duplicados.esLaMisma("https://ejemplo.com/ver?id=1", "https://ejemplo.com/ver?id=2"))
    }

    @Test("el orden de los parámetros da igual")
    func ordenDaIgual() {
        #expect(
            Duplicados.esLaMisma("https://ejemplo.com/?b=2&a=1", "https://ejemplo.com/?a=1&b=2"))
    }

    @Test("la ruta distingue mayúsculas: hay servidores donde no es lo mismo")
    func rutaDistingueMayusculas() {
        #expect(!Duplicados.esLaMisma("https://ejemplo.com/Uno", "https://ejemplo.com/uno"))
    }

    @Test("lo que no es una URL se compara tal cual, sin inventar duplicados")
    func loQueNoEsUrl() {
        #expect(Duplicados.normalizar("  esto no es una url  ") == "esto no es una url")
        #expect(!Duplicados.esLaMisma("esto no es una url", "ni esto tampoco"))
    }

    @Test("dos cosas que no son URL tampoco se confunden entre sí por estar vacías")
    func sinAnfitrion() {
        // El fallo que esto caza es el de la app de Expo: allí el `URL` de
        // React Native devolvía anfitrión vacío para cualquier cosa, así que
        // todo lo que no fuera una URL normalizaba igual y se tomaba por
        // duplicado de lo anterior.
        #expect(Duplicados.normalizar("hola") != Duplicados.normalizar("adios"))
    }
}

@Suite("Buscar un duplicado en lo ya guardado")
struct PruebasBuscarDuplicado {
    private let guardados = [
        Elemento(id: "e1", url: "https://www.xataka.com/basics/alternativas-pocket"),
        Elemento(id: "e2", url: "https://ejemplo.com/otro"),
    ]

    @Test("encuentra el que ya estaba, aunque venga escrito de otra forma")
    func loEncuentra() {
        let encontrado = Duplicados.buscar(
            en: guardados,
            url: "http://xataka.com/basics/alternativas-pocket/?utm_source=twitter"
        )

        #expect(encontrado?.id == "e1")
    }

    @Test("una URL nueva no es duplicado de nada")
    func urlNueva() {
        #expect(Duplicados.buscar(en: guardados, url: "https://ejemplo.com/nuevo") == nil)
    }

    @Test("lo borrado no cuenta: volver a guardarlo es un alta normal")
    func loBorradoNoCuenta() {
        let conBorrado = [Elemento(id: "e3", url: "https://ejemplo.com/ido", borrado: true)]

        #expect(Duplicados.buscar(en: conBorrado, url: "https://ejemplo.com/ido") == nil)
    }
}
