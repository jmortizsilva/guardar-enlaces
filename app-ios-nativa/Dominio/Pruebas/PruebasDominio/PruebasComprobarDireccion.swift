import Foundation
import Testing

@testable import Dominio

/// Responde según la dirección, y apunta cuáles se han preguntado.
private actor Respuestas {
    let porDireccion: [String: Comprobacion]
    var preguntadas: [String] = []

    init(_ porDireccion: [String: Comprobacion]) {
        self.porDireccion = porDireccion
    }

    func comprobar(_ url: String) -> Comprobacion {
        preguntadas.append(url)
        return porDireccion[url] ?? .sinComprobar
    }
}

@Suite("Qué dirección se guarda según cargue o no")
struct PruebasComprobarDireccion {
    let completada = DireccionEscrita(direccion: "https://viejo.es", alternativa: "http://viejo.es")
    let escritaEntera = DireccionEscrita(direccion: "https://viejo.es", alternativa: nil)
    let carga = Comprobacion.carga(MetadatosExtraidos(titulo: "Viejo"))

    @Test("si carga con https no se prueba nada más")
    func cargaALaPrimera() async {
        let respuestas = Respuestas(["https://viejo.es": carga])
        let resultado = await ComprobarDireccion.comprobar(completada) {
            await respuestas.comprobar($0)
        }
        #expect(
            resultado == DireccionComprobada(direccion: "https://viejo.es", comprobacion: carga))
        #expect(await respuestas.preguntadas == ["https://viejo.es"])
    }

    @Test("si no carga con https y lo puso la app, se queda con http si carga")
    func cargaConHttp() async {
        let respuestas = Respuestas(["https://viejo.es": .noCarga, "http://viejo.es": carga])
        let resultado = await ComprobarDireccion.comprobar(completada) {
            await respuestas.comprobar($0)
        }
        #expect(resultado == DireccionComprobada(direccion: "http://viejo.es", comprobacion: carga))
    }

    @Test("si no carga ninguna, se queda con la de https")
    func noCargaNinguna() async {
        let respuestas = Respuestas(["https://viejo.es": .noCarga, "http://viejo.es": .noCarga])
        let resultado = await ComprobarDireccion.comprobar(completada) {
            await respuestas.comprobar($0)
        }
        #expect(
            resultado == DireccionComprobada(direccion: "https://viejo.es", comprobacion: .noCarga)
        )
    }

    @Test("si el esquema lo escribió quien usa la app, no se prueba otro")
    func esquemaEscrito() async {
        let respuestas = Respuestas(["https://viejo.es": .noCarga])
        let resultado = await ComprobarDireccion.comprobar(escritaEntera) {
            await respuestas.comprobar($0)
        }
        #expect(resultado.comprobacion == .noCarga)
        #expect(await respuestas.preguntadas == ["https://viejo.es"])
    }

    @Test("sin poder comprobar no se prueba la alternativa")
    func sinComprobar() async {
        let respuestas = Respuestas(["https://viejo.es": .sinComprobar])
        let resultado = await ComprobarDireccion.comprobar(completada) {
            await respuestas.comprobar($0)
        }
        #expect(resultado.comprobacion == .sinComprobar)
        #expect(await respuestas.preguntadas == ["https://viejo.es"])
    }
}
