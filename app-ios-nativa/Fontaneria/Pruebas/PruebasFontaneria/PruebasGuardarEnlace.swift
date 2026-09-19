import Dominio
import Foundation
import Testing

@testable import Fontaneria

@Suite("Guardar un enlace, desde donde sea")
struct PruebasGuardarEnlace {
    private func almacen() throws -> AlmacenLocal {
        try AlmacenLocal(ruta: ":memory:")
    }

    private let metadatos = MetadatosExtraidos(
        titulo: "Alternativas a Pocket",
        descripcion: "Una lista",
        imagenUrl: nil,
        tipo: .enlace
    )

    @Test("un enlace que no estaba se guarda y queda encolado")
    func enlaceNuevo() throws {
        let almacen = try almacen()

        let resultado = try GuardarEnlace.guardar(
            url: "https://ejemplo.com",
            etiquetas: ["ocio"],
            metadatos: metadatos,
            en: almacen,
            ahora: { 1_000 }
        )

        guard case .nuevo(let elemento) = resultado else {
            Issue.record("se esperaba un enlace nuevo")
            return
        }
        #expect(elemento.titulo == "Alternativas a Pocket")
        #expect(elemento.etiquetas == ["ocio"])
        #expect(try almacen.cargarPendientes()[elemento.id] != nil)
    }

    @Test("el enlace repetido actualiza el que había, como en el ordenador")
    func enlaceRepetido() throws {
        let almacen = try almacen()
        let primero = try GuardarEnlace.guardar(
            url: "https://ejemplo.com",
            etiquetas: ["ocio"],
            metadatos: metadatos,
            en: almacen,
            ahora: { 1_000 }
        )

        let segundo = try GuardarEnlace.guardar(
            url: "https://ejemplo.com",
            etiquetas: ["trabajo"],
            metadatos: nil,
            en: almacen,
            ahora: { 2_000 }
        )

        guard case .actualizado(let elemento) = segundo else {
            Issue.record("se esperaba una actualización")
            return
        }
        #expect(elemento.id == primero.elemento.id)
        #expect(elemento.etiquetas == ["ocio", "trabajo"])
        // Sin metadatos nuevos se conserva el título: quedarse sin él por
        // compartir el mismo enlace sin cobertura sería perder información.
        #expect(elemento.titulo == "Alternativas a Pocket")
        #expect(try almacen.cargarTodos().count == 1)
    }

    @Test("un enlace borrado y vuelto a guardar no deja dos filas")
    func enlaceQueSeHabiaBorrado() throws {
        let almacen = try almacen()
        let primero = try GuardarEnlace.guardar(
            url: "https://ejemplo.com",
            etiquetas: [],
            metadatos: metadatos,
            en: almacen,
            ahora: { 1_000 }
        )
        try almacen.marcarPendiente(primero.elemento.marcadoComoBorrado(ahora: { 1_500 }))

        let segundo = try GuardarEnlace.guardar(
            url: "https://ejemplo.com",
            etiquetas: [],
            metadatos: metadatos,
            en: almacen,
            ahora: { 2_000 }
        )

        // El borrado no cuenta como repetido: la fila sigue ahí de lápida,
        // pero para quien guarda el enlace ya no existe.
        guard case .nuevo = segundo else {
            Issue.record("un enlace borrado tiene que volver como nuevo")
            return
        }
    }

    @Test("lo que se dice al guardar distingue los tres casos")
    func loQueSeDice() throws {
        let almacen = try almacen()
        let nuevo = try GuardarEnlace.guardar(
            url: "https://ejemplo.com",
            etiquetas: [],
            metadatos: metadatos,
            en: almacen,
            ahora: { 1_000 }
        )

        #expect(nuevo.anuncio(seComprobo: true) == "Guardado, Alternativas a Pocket")
        #expect(
            nuevo.anuncio(seComprobo: false)
                == "Guardado sin título, no se pudo comprobar la página"
        )

        let repetido = try GuardarEnlace.guardar(
            url: "https://ejemplo.com",
            etiquetas: [],
            metadatos: metadatos,
            en: almacen,
            ahora: { 2_000 }
        )
        #expect(repetido.anuncio(seComprobo: true) == "Actualizado, Alternativas a Pocket")
    }
}
