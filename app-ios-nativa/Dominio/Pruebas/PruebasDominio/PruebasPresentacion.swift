import Foundation
import Testing

@testable import Dominio

/// Mediodía UTC del 15 de marzo de 2024. A mediodía, y con la zona horaria
/// fijada en la prueba, la fecha no se va al día de antes ni al de después
/// según dónde se ejecute esto.
private let mediodiaUtc: MarcaDeTiempo = 1_710_504_000_000

private func elemento(
    url: String,
    titulo: String? = nil,
    etiquetas: [String] = [],
    creadoEn: MarcaDeTiempo = mediodiaUtc
) -> Elemento {
    Elemento(
        id: "e1",
        url: url,
        titulo: titulo,
        etiquetas: etiquetas,
        creadoEn: creadoEn,
        // Muy posterior a la de guardado: la fila no debe enseñar esta.
        actualizadoEn: creadoEn + 30 * 86_400_000
    )
}

private func subtitulo(_ elemento: Elemento) -> String {
    Presentacion.subtitulo(de: elemento, zonaHoraria: TimeZone(identifier: "UTC")!)
}

@Suite("Título de la fila")
struct PruebasTituloFila {
    @Test("usa el título cuando lo hay")
    func conTitulo() {
        #expect(Presentacion.titulo(de: elemento(url: "https://a.com", titulo: "A")) == "A")
    }

    @Test("sin título usa la URL, que es mejor que una fila muda")
    func sinTitulo() {
        #expect(Presentacion.titulo(de: elemento(url: "https://a.com")) == "https://a.com")
    }

    @Test("un título vacío cuenta como no tener título")
    func tituloVacio() {
        let sinNada = elemento(url: "https://a.com", titulo: "")

        #expect(Presentacion.titulo(de: sinNada) == "https://a.com")
    }
}

@Suite("Subtítulo de la fila")
struct PruebasSubtituloFila {
    @Test("lleva dominio, etiquetas y fecha, en ese orden")
    func completo() {
        let uno = elemento(
            url: "https://www.ejemplo.com/articulo",
            titulo: "Un artículo interesante",
            etiquetas: ["ocio", "pendiente"]
        )

        #expect(subtitulo(uno) == "www.ejemplo.com — ocio, pendiente — 15 de marzo de 2024")
    }

    @Test("la fecha lleva el mes en letra, que es como se oye bien")
    func mesEnLetra() {
        #expect(subtitulo(elemento(url: "https://a.com")).contains("de marzo de"))
    }

    @Test("sin título el subtítulo sigue diciendo el dominio")
    func sinTitulo() {
        #expect(subtitulo(elemento(url: "https://a.com")).hasPrefix("a.com"))
    }

    @Test("sin etiquetas no queda un separador vacío en medio")
    func sinEtiquetas() {
        #expect(!subtitulo(elemento(url: "https://a.com", titulo: "A")).contains(" —  — "))
    }

    @Test("sin fecha lo dice, en vez de soltar 1970")
    func sinFecha() {
        #expect(subtitulo(elemento(url: "https://a.com", creadoEn: 0)).contains("sin fecha"))
    }

    @Test("la fecha es la de cuando se guardó, no la del último cambio")
    func fechaDeGuardadoNoDeCambio() {
        // Cambiarle una etiqueta a un enlace de hace un mes movía la fecha que
        // se lee en la fila, y entonces ya no había forma de saber cuándo se
        // había guardado de verdad.
        let guardadoEnMarzo = elemento(url: "https://a.com", titulo: "A")
        let retocadoHoy = guardadoEnMarzo.conEtiquetas(
            ["ocio"], ahora: { mediodiaUtc + 99_999_999 })

        #expect(subtitulo(retocadoHoy).contains("15 de marzo de 2024"))
    }

    @Test("lo que no es una URL no aporta dominio, pero tampoco rompe la fila")
    func urlIlegible() {
        let raro = elemento(url: "esto no es una url", titulo: "Raro")

        #expect(subtitulo(raro) == "15 de marzo de 2024")
    }
}

@Suite("Textos del detalle")
struct PruebasTextosDetalle {
    @Test("la fecha de guardado se lee, no se descifra")
    func fechaDeGuardado() {
        let fecha = Presentacion.fechaLegible(
            mediodiaUtc,
            locale: Presentacion.localeDeLaApp,
            zonaHoraria: TimeZone(identifier: "UTC")!
        )

        #expect(Textos.guardadoEl(fecha) == "Guardado el 15 de marzo de 2024")
    }

    @Test("un enlace sin fecha lo dice en vez de soltar 1970")
    func sinFecha() {
        let fecha = Presentacion.fechaLegible(
            0,
            locale: Presentacion.localeDeLaApp,
            zonaHoraria: TimeZone(identifier: "UTC")!
        )

        #expect(fecha == "sin fecha")
    }
}
