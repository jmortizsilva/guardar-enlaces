import Foundation
import Testing

@testable import Dominio

@Suite("El nonce del inicio de sesión de Apple")
struct PruebasNonce {
    @Test("el resumen es el SHA-256 del valor en claro, en hexadecimal")
    func resumenCorrecto() {
        // Con un aleatorio fijo, el valor en claro son 32 bytes a cero en hexadecimal, y su
        // SHA-256 es este, comprobable con `printf '000...' | shasum -a 256`.
        let nonce = Login.nonceParaApple(aleatorio: { Array(repeating: 0, count: $0) })

        #expect(nonce.enClaro == String(repeating: "00", count: 32))
        #expect(nonce.resumen.count == 64)
        #expect(nonce.resumen.allSatisfy { $0.isHexDigit })
    }

    @Test("dos seguidos no se repiten")
    func noSeRepite() {
        #expect(Login.nonceParaApple().enClaro != Login.nonceParaApple().enClaro)
    }

    @Test("el valor en claro es largo: 32 bytes, no cuatro letras")
    func suficienteEntropia() {
        #expect(Login.nonceParaApple().enClaro.count == 64)
    }
}
