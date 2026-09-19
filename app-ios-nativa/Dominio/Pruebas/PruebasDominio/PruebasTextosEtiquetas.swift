import Foundation
import Testing

@testable import Dominio

@Suite("Textos de gestionar etiquetas")
struct PruebasTextosEtiquetas {
    @Test("cada fila dice cuántos enlaces lleva la etiqueta, con su plural")
    func recuento() {
        #expect(Textos.etiquetaConRecuento("ocio", enlaces: 0) == "ocio, ningún enlace")
        #expect(Textos.etiquetaConRecuento("ocio", enlaces: 1) == "ocio, 1 enlace")
        #expect(Textos.etiquetaConRecuento("ocio", enlaces: 5) == "ocio, 5 enlaces")
    }

    @Test("antes de eliminar se dice a cuántos enlaces afecta y que no se deshace")
    func preguntaEliminar() {
        #expect(
            Textos.preguntaEliminarEtiqueta("ocio", enlaces: 1)
                == "¿Eliminar la etiqueta «ocio»? Se quitará de 1 enlace y no se puede deshacer."
        )
        #expect(Textos.preguntaEliminarEtiqueta("ocio", enlaces: 3).contains("de 3 enlaces"))
    }

    @Test("al renombrar se dice en cuántos enlaces ha cambiado")
    func renombrada() {
        #expect(
            Textos.etiquetaRenombrada(de: "ocio", a: "tiempo libre", enlaces: 2)
                == "Etiqueta «ocio» renombrada a «tiempo libre» en 2 enlaces"
        )
        #expect(Textos.etiquetaRenombrada(de: "a", a: "b", enlaces: 1).hasSuffix("en 1 enlace"))
    }

    @Test("al eliminar y al crear también se dice el resultado")
    func eliminadaYAnadida() {
        #expect(
            Textos.etiquetaEliminada("ocio", enlaces: 4) == "Etiqueta «ocio» eliminada de 4 enlaces"
        )
        #expect(Textos.etiquetaAnadida("casa") == "Etiqueta «casa» añadida")
    }
}
