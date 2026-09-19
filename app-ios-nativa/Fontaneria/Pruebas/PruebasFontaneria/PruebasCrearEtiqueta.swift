import Dominio
import Foundation
import Testing

@testable import Fontaneria

@Suite("Crear una etiqueta al vuelo")
struct PruebasCrearEtiqueta {
    private func almacen() throws -> AlmacenLocal {
        try AlmacenLocal(ruta: ":memory:")
    }

    @Test("la etiqueta nueva queda guardada y encolada")
    func etiquetaNueva() throws {
        let almacen = try almacen()

        let etiqueta = try CrearEtiqueta.crear(
            nombre: "ocio",
            entre: [],
            en: almacen,
            ahora: { 1_000 }
        )

        #expect(etiqueta?.nombre == "ocio")
        #expect(try almacen.cargarEtiquetasDefinidas().values.contains { $0.nombre == "ocio" })
        #expect(try almacen.cargarEtiquetasPendientes().count == 1)
    }

    @Test("los espacios de alrededor no cuentan")
    func nombreConEspacios() throws {
        let almacen = try almacen()

        let etiqueta = try CrearEtiqueta.crear(
            nombre: "  trabajo  ",
            entre: [],
            en: almacen,
            ahora: { 1_000 }
        )

        #expect(etiqueta?.nombre == "trabajo")
    }

    @Test("una que ya existe no se duplica, y no es un error")
    func etiquetaRepetida() throws {
        let almacen = try almacen()

        let etiqueta = try CrearEtiqueta.crear(
            nombre: "ocio",
            entre: ["ocio"],
            en: almacen,
            ahora: { 1_000 }
        )

        #expect(etiqueta == nil)
        #expect(try almacen.cargarEtiquetasDefinidas().isEmpty)
    }

    @Test("un nombre en blanco no crea nada")
    func nombreEnBlanco() throws {
        let almacen = try almacen()

        #expect(try CrearEtiqueta.crear(nombre: "   ", entre: [], en: almacen) == nil)
        #expect(try almacen.cargarEtiquetasDefinidas().isEmpty)
    }
}
