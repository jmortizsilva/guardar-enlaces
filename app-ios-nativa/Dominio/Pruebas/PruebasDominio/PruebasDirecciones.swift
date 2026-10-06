import Foundation
import Testing

@testable import Dominio

/// Como las de importar, estas pruebas no traen sus casos: leen
/// `pruebas-compartidas/direcciones/casos.json`, el mismo que leen Windows y
/// Android.
private let casos = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()  // PruebasDominio
    .deletingLastPathComponent()  // Pruebas
    .deletingLastPathComponent()  // Dominio
    .deletingLastPathComponent()  // app-ios-nativa
    .deletingLastPathComponent()  // raíz del repositorio
    .appendingPathComponent("pruebas-compartidas/direcciones/casos.json")

@Suite("Completar la dirección escrita a mano")
struct PruebasDirecciones {
    @Test("completa como dicen los casos compartidos")
    func casosCompartidos() throws {
        let datos = try Data(contentsOf: casos)
        let todo = try #require(JSONSerialization.jsonObject(with: datos) as? [String: Any])
        let lista = try #require(todo["casos"] as? [[String: Any]])
        #expect(lista.count > 10)
        for caso in lista {
            let escrito = try #require(caso["escrito"] as? String)
            let resultado = Enlaces.completar(escrito)
            if let direccion = caso["direccion"] as? String {
                #expect(
                    resultado
                        == DireccionEscrita(
                            direccion: direccion,
                            alternativa: caso["alternativa"] as? String
                        ),
                    "con «\(escrito)»"
                )
            } else {
                #expect(resultado == nil, "con «\(escrito)»")
            }
        }
    }

    @Test("la pregunta cuando no carga dice la dirección como se escribió")
    func textoNoCarga() {
        #expect(
            Textos.noCarga("noexiste.es")
                == "noexiste.es no ha respondido. Puede que la dirección esté mal escrita, o que la página no deje comprobarla."
        )
    }
}
