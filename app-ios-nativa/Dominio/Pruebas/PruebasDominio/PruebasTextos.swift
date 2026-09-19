import Foundation
import Testing

@testable import Dominio

@Suite("Textos de la lista")
struct PruebasTextosLista {
    @Test("el filtro dice por dónde está filtrando")
    func filtro() {
        #expect(Textos.filtroPorEtiqueta(nil) == "Filtrar por etiqueta: Todas")
        #expect(Textos.filtroPorEtiqueta("ocio") == "Filtrar por etiqueta: ocio")
    }

    @Test("la lista vacía distingue no tener nada de que el filtro no encuentre")
    func listaVacia() {
        #expect(
            Textos.listaVacia(busqueda: "", etiqueta: nil)
                == "No hay enlaces guardados todavía."
        )
        #expect(
            Textos.listaVacia(busqueda: "cookies", etiqueta: nil)
                == "Ningún enlace con «cookies»."
        )
        #expect(
            Textos.listaVacia(busqueda: "", etiqueta: "ocio")
                == "Ningún enlace con la etiqueta «ocio»."
        )
        #expect(
            Textos.listaVacia(busqueda: "cookies", etiqueta: "ocio")
                == "Ningún enlace con «cookies» y la etiqueta «ocio»."
        )
    }

    @Test("una búsqueda de solo espacios cuenta como no buscar nada")
    func busquedaEnBlanco() {
        #expect(
            Textos.listaVacia(busqueda: "   ", etiqueta: nil)
                == "No hay enlaces guardados todavía."
        )
    }
}

@Suite("Textos de confirmación y de voz")
struct PruebasTextosAnuncios {
    @Test("la pregunta de eliminar dice qué se elimina")
    func preguntaEliminar() {
        #expect(
            Textos.preguntaEliminar(titulo: "Alternativas a Pocket")
                == "¿Eliminar «Alternativas a Pocket»?")
    }

    @Test("con cuenta avisa de que también se va del ordenador")
    func consecuenciaEliminar() {
        #expect(
            Textos.consecuenciaEliminar(conCuenta: true) == "Se eliminará también en el ordenador.")
        #expect(
            Textos.consecuenciaEliminar(conCuenta: false) == "Está guardado solo en este iPhone."
        )
    }

    @Test("al eliminar se oye primero la acción y después el enlace")
    func anuncioEliminado() {
        #expect(
            Textos.eliminado(titulo: "Alternativas a Pocket") == "Eliminado, Alternativas a Pocket")
    }

    @Test("las etiquetas se anuncian por cómo quedan, no por que se hayan guardado")
    func anuncioEtiquetas() {
        #expect(Textos.etiquetasGuardadas(["ocio", "pendiente"]) == "Etiquetas: ocio, pendiente")
        #expect(Textos.etiquetasGuardadas([]) == "Sin etiquetas")
    }

    @Test("el plural de la sincronización está concordado, sin paréntesis")
    func anuncioSincronizacion() {
        #expect(Textos.sincronizacionTerminada(enlacesNuevos: 0) == "Sincronizado, sin cambios")
        #expect(Textos.sincronizacionTerminada(enlacesNuevos: 1) == "Sincronizado, 1 enlace nuevo")
        #expect(
            Textos.sincronizacionTerminada(enlacesNuevos: 3) == "Sincronizado, 3 enlaces nuevos")
    }

    @Test("el fallo lleva la acción delante y la causa detrás")
    func anuncioFallo() {
        #expect(
            Textos.sincronizacionFallida(causa: "sin conexión con el servidor")
                == "No se pudo sincronizar: sin conexión con el servidor"
        )
    }
}

@Suite("Recortar títulos largos")
struct PruebasRecorte {
    @Test("lo corto se queda igual")
    func textoCorto() {
        #expect(Textos.recortado("Alternativas a Pocket") == "Alternativas a Pocket")
    }

    @Test("lo largo se corta por la última palabra que cabe")
    func textoLargo() {
        let largo =
            "Las mejores alternativas a Pocket para guardar artículos y leerlos más tarde en cualquier dispositivo"

        let corto = Textos.recortado(largo)

        #expect(corto.count <= 61)
        #expect(corto.hasSuffix("…"))
        // Cortar a mitad de palabra suena a error de la aplicación.
        #expect(!corto.contains("artícu…"))
    }

    @Test("una palabra sola larguísima se corta igual, aunque quede partida")
    func palabraUnica() {
        let corto = Textos.recortado(String(repeating: "a", count: 100))

        #expect(corto.count == 61)
        #expect(corto.hasSuffix("…"))
    }
}

@Suite("Aviso del portapapeles")
struct PruebasAvisoPortapapeles {
    @Test("dice que hay algo copiado y qué se puede hacer, sin explicar la app")
    func aviso() {
        #expect(Textos.hayEnlaceCopiado == "Hay un enlace copiado, puedes pegarlo")
    }
}
