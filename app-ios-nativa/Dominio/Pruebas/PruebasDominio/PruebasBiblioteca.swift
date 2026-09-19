import Foundation
import Testing

@testable import Dominio

private func elemento(
    _ id: String,
    url: String = "https://a.com",
    titulo: String? = nil,
    etiquetas: [String] = []
) -> Elemento {
    Elemento(id: id, url: url, titulo: titulo, etiquetas: etiquetas, actualizadoEn: 100)
}

@Suite("Buscar en la lista")
struct PruebasBuscar {
    private let a = elemento("a", url: "https://python.org", titulo: "Documentación Python")
    private let b = elemento(
        "b", url: "https://otra.com", titulo: "Otra cosa", etiquetas: ["python"])
    private let c = elemento("c", url: "https://nada.com", titulo: "Nada que ver")

    @Test("busca en título, URL y etiquetas, sin distinguir mayúsculas")
    func buscaEnLosTresSitios() {
        #expect(Biblioteca.buscar([a, b, c], texto: "PYTHON").map(\.id) == ["a", "b"])
    }

    @Test("sin texto no filtra")
    func sinTexto() {
        #expect(Biblioteca.buscar([a, b, c], texto: "").count == 3)
        #expect(Biblioteca.buscar([a, b, c], texto: "   ").count == 3)
    }

    @Test("lo que no está no aparece")
    func sinResultados() {
        #expect(Biblioteca.buscar([a, b, c], texto: "no-existe").isEmpty)
    }
}

@Suite("Etiquetas en uso")
struct PruebasEtiquetasEnUso {
    @Test("las distintas, ordenadas y sin repetir")
    func distintasOrdenadas() {
        let a = elemento("a", etiquetas: ["ocio", "pendiente"])
        let b = elemento("b", etiquetas: ["trabajo", "ocio"])
        let c = elemento("c")

        #expect(Biblioteca.etiquetasEnUso([a, b, c]) == ["ocio", "pendiente", "trabajo"])
    }

    @Test("sin elementos, o sin etiquetas, no hay ninguna")
    func vacio() {
        #expect(Biblioteca.etiquetasEnUso([]).isEmpty)
        #expect(Biblioteca.etiquetasEnUso([elemento("a")]).isEmpty)
    }

    @Test("cuenta cuántos enlaces lleva cada una")
    func recuento() {
        let a = elemento("a", etiquetas: ["ocio", "trabajo"])
        let b = elemento("b", etiquetas: ["ocio"])
        let c = elemento("c")

        #expect(Biblioteca.recuentoPorEtiqueta([a, b, c]) == ["ocio": 2, "trabajo": 1])
    }
}

@Suite("Filtrar por etiqueta")
struct PruebasFiltrarPorEtiqueta {
    private let a = elemento("a", etiquetas: ["ocio"])
    private let b = elemento("b", etiquetas: ["trabajo"])

    @Test("deja los que la llevan")
    func filtra() {
        #expect(Biblioteca.filtrarPorEtiqueta([a, b], etiqueta: "ocio").map(\.id) == ["a"])
    }

    @Test("sin etiqueta elegida no filtra")
    func sinEtiqueta() {
        #expect(Biblioteca.filtrarPorEtiqueta([a, b], etiqueta: nil).count == 2)
        #expect(Biblioteca.filtrarPorEtiqueta([a, b], etiqueta: "").count == 2)
    }
}

@Suite("Renombrar y quitar una etiqueta")
struct PruebasRenombrarEtiqueta {
    @Test("devuelve solo los enlaces que cambian, con la fecha movida")
    func soloLosQueCambian() {
        let a = elemento("a", etiquetas: ["ocio"])
        let b = elemento("b", etiquetas: ["trabajo"])

        let cambiados = Biblioteca.renombrarEtiqueta(
            en: [a, b],
            vieja: "ocio",
            nueva: "tiempo libre",
            ahora: relojFijo(200)
        )

        #expect(cambiados.map(\.id) == ["a"])
        #expect(cambiados[0].etiquetas == ["tiempo libre"])
        #expect(cambiados[0].actualizadoEn == 200)
    }

    @Test("renombrar a una que el enlace ya tenía las funde, no la repite")
    func fusionSinRepetir() {
        let a = elemento("a", etiquetas: ["ocio", "casa"])

        let cambiados = Biblioteca.renombrarEtiqueta(en: [a], vieja: "ocio", nueva: "casa")

        #expect(cambiados[0].etiquetas == ["casa"])
    }

    @Test("quitar una etiqueta deja el resto en paz")
    func quitarUna() {
        let a = elemento("a", etiquetas: ["ocio", "casa"])
        let b = elemento("b", etiquetas: ["trabajo"])

        let cambiados = Biblioteca.quitarEtiqueta(
            en: [a, b], etiqueta: "ocio", ahora: relojFijo(200))

        #expect(cambiados.map(\.id) == ["a"])
        #expect(cambiados[0].etiquetas == ["casa"])
        #expect(cambiados[0].actualizadoEn == 200)
    }
}
