import Foundation
import Testing

@testable import Dominio

@Suite("Qué cuenta como enlace")
struct PruebasEnlaces {
    @Test("se aceptan las dos formas de dirección")
    func direccionesBuenas() {
        #expect(Enlaces.esDireccion("https://ejemplo.com"))
        #expect(Enlaces.esDireccion("http://ejemplo.com"))
        #expect(Enlaces.esDireccion("  https://ejemplo.com  "))
    }

    @Test("lo que no es una dirección se rechaza")
    func direccionesMalas() {
        #expect(!Enlaces.esDireccion("ejemplo.com"))
        #expect(!Enlaces.esDireccion("nota para el lunes"))
        #expect(!Enlaces.esDireccion(""))
        #expect(!Enlaces.esDireccion("   "))
    }

    @Test("el esquema a secas no es una dirección")
    func esquemaSinNada() {
        #expect(!Enlaces.esDireccion("https://"))
        #expect(!Enlaces.esDireccion("http://"))
    }

    @Test("la dirección se saca de la frase en la que venga")
    func direccionDentroDeUnaFrase() {
        #expect(
            Enlaces.direccionDentroDe("Mira esto: https://ejemplo.com/articulo")
                == "https://ejemplo.com/articulo"
        )
        #expect(
            Enlaces.direccionDentroDe("https://ejemplo.com vía @alguien")
                == "https://ejemplo.com"
        )
    }

    @Test("una dirección sola vuelve tal cual, sin los espacios de alrededor")
    func direccionSola() {
        #expect(Enlaces.direccionDentroDe(" https://ejemplo.com\n") == "https://ejemplo.com")
    }

    @Test("un texto sin ninguna dirección no devuelve nada")
    func sinDireccion() {
        #expect(Enlaces.direccionDentroDe("la lista de la compra") == nil)
        #expect(Enlaces.direccionDentroDe("") == nil)
    }

    @Test("con dos direcciones se queda con la primera")
    func dosDirecciones() {
        #expect(
            Enlaces.direccionDentroDe("https://uno.com y también https://dos.com")
                == "https://uno.com"
        )
    }
}
