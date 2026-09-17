import Foundation

@testable import Dominio

/// Identificadores predecibles (`id-de-prueba-1`, `-2`...). Sin esto habría
/// que comparar contra uuid aleatorios, que no se pueden comprobar.
func generadorSecuencial() -> GeneradorId {
    var contador = 0
    return {
        contador += 1
        return "id-de-prueba-\(contador)"
    }
}

func relojFijo(_ instante: MarcaDeTiempo) -> Reloj {
    { instante }
}

/// Decodifica un JSON escrito a mano en la prueba. Sirve para comprobar que
/// se tolera lo que el servidor puede dejarse sin mandar.
func desdeJson<T: Decodable>(_ json: String) throws -> T {
    try JSONDecoder().decode(T.self, from: Data(json.utf8))
}

/// Codifica y vuelve a decodificar: comprueba que el viaje de ida y vuelta
/// al JSON del contrato no pierde nada por el camino.
func idaYVuelta<T: Codable>(_ valor: T) throws -> T {
    try JSONDecoder().decode(T.self, from: JSONEncoder().encode(valor))
}
