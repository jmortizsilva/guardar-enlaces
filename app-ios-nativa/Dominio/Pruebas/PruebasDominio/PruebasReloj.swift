import Foundation
import Testing

@testable import Dominio

@Suite("Reloj")
struct PruebasReloj {
    @Test("el reloj del sistema cuenta en milisegundos, no en segundos")
    func cuentaEnMilisegundos() {
        // 1.700.000.000.000 ms es noviembre de 2023. El mismo instante
        // contado en segundos da un número mil veces menor, que es justo el
        // fallo que esta comprobación tiene que cazar.
        #expect(relojDelSistema() > 1_700_000_000_000)
    }

    @Test("un reloj inyectado devuelve siempre el mismo instante")
    func relojInyectadoEsFijo() {
        let fijo: Reloj = { 1_735_000_000_000 }
        #expect(fijo() == fijo())
    }
}
