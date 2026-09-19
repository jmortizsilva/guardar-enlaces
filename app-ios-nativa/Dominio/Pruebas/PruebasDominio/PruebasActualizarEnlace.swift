import Foundation
import Testing

@testable import Dominio

@Suite("Guardar un enlace que ya estaba")
struct PruebasActualizarEnlace {
    private let guardado = Elemento(
        id: "e1",
        url: "https://a.com",
        titulo: "Título de antes",
        descripcion: "Descripción de antes",
        imagenUrl: "https://a.com/vieja.jpg",
        tipo: .enlace,
        etiquetas: ["ocio"],
        creadoEn: 100,
        actualizadoEn: 100
    )

    @Test("se refresca con lo que diga la comprobación de ahora")
    func refrescaMetadatos() {
        let nuevos = MetadatosExtraidos(
            titulo: "Título de ahora",
            descripcion: "Descripción de ahora",
            imagenUrl: "https://a.com/nueva.jpg",
            tipo: .articulo
        )

        let resultado = guardado.actualizado(con: nuevos, ahora: relojFijo(500))

        #expect(resultado.id == "e1")
        #expect(resultado.titulo == "Título de ahora")
        #expect(resultado.tipo == .articulo)
        #expect(resultado.actualizadoEn == 500)
        #expect(resultado.creadoEn == 100)
    }

    @Test("si la comprobación no trajo nada, se conserva lo que ya había")
    func sinComprobacionConservaLoAnterior() {
        let resultado = guardado.actualizado(con: nil, ahora: relojFijo(500))

        #expect(resultado.titulo == "Título de antes")
        #expect(resultado.descripcion == "Descripción de antes")
        #expect(resultado.imagenUrl == "https://a.com/vieja.jpg")
    }

    @Test("las etiquetas se suman, no se sustituyen")
    func etiquetasSeSuman() {
        let resultado = guardado.actualizado(
            con: nil,
            etiquetasNuevas: ["pendiente"],
            ahora: relojFijo(500)
        )

        // Quitar en silencio una etiqueta puesta hace un mes sería peor que
        // no guardar nada.
        #expect(resultado.etiquetas == ["ocio", "pendiente"])
    }

    @Test("una etiqueta que ya llevaba no se repite")
    func etiquetasSinRepetir() {
        let resultado = guardado.actualizado(con: nil, etiquetasNuevas: ["ocio", "casa"])

        #expect(resultado.etiquetas == ["ocio", "casa"])
    }

    @Test("un título que llega vacío no borra el que había")
    func tituloVacio() {
        let sinTitulo = MetadatosExtraidos(titulo: nil, descripcion: nil, imagenUrl: nil)

        let resultado = guardado.actualizado(con: sinTitulo)

        #expect(resultado.titulo == "Título de antes")
    }
}

@Suite("Textos de añadir un enlace")
struct PruebasTextosAnadir {
    @Test("el botón de etiquetas dice cuáles llevas")
    func botonEtiquetas() {
        #expect(Textos.botonEtiquetas([]) == "Etiquetas: ninguna")
        #expect(Textos.botonEtiquetas(["ocio", "pendiente"]) == "Etiquetas: ocio, pendiente")
    }

    @Test("guardar y actualizar se anuncian distinto, que no es lo mismo")
    func anuncios() {
        #expect(Textos.guardado(titulo: "Un artículo") == "Guardado, Un artículo")
        #expect(Textos.actualizado(titulo: "Un artículo") == "Actualizado, Un artículo")
    }

    @Test("si la comprobación falló, el anuncio dice qué faltó")
    func guardadoSinComprobar() {
        #expect(
            Textos.guardadoSinComprobar == "Guardado sin título, no se pudo comprobar la página"
        )
    }
}
