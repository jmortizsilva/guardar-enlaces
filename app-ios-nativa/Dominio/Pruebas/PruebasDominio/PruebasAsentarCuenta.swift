import Foundation
import Testing

@testable import Dominio

private let cuenta = AsentarCuenta.identidadDueno(
    urlServidor: "https://api.ejemplo.com",
    email: "persona@ejemplo.com"
)
private let otraCuenta = AsentarCuenta.identidadDueno(
    urlServidor: "https://api.ejemplo.com",
    email: "otra@ejemplo.com"
)

@Suite("Entrar en una cuenta con enlaces ya en el teléfono")
struct PruebasAsentarCuenta {
    @Test("entrar en la cuenta de siempre no pregunta ni toca nada")
    func mismaCuenta() {
        let paso = AsentarCuenta.alEntrar(
            duenoAnterior: cuenta,
            dueno: cuenta,
            cuantosElementos: 12
        )

        #expect(paso == .noHacerNada)
    }

    @Test("con el teléfono vacío no se pregunta: no hay nada que decidir")
    func telefonoVacio() {
        let paso = AsentarCuenta.alEntrar(duenoAnterior: nil, dueno: cuenta, cuantosElementos: 0)

        #expect(paso == .asentar(.empezarDeCero))
    }

    @Test("lo guardado sin cuenta se pregunta, diciendo que no era de otra")
    func guardadoSinCuenta() {
        let paso = AsentarCuenta.alEntrar(duenoAnterior: nil, dueno: cuenta, cuantosElementos: 3)

        #expect(paso == .preguntar(EnlacesEnElTelefono(cuantos: 3, deOtraCuenta: false)))
    }

    @Test("al cambiar de cuenta se avisa de que lo de antes era de otra")
    func cambioDeCuenta() {
        let paso = AsentarCuenta.alEntrar(
            duenoAnterior: otraCuenta,
            dueno: cuenta,
            cuantosElementos: 5
        )

        #expect(paso == .preguntar(EnlacesEnElTelefono(cuantos: 5, deOtraCuenta: true)))
    }

    @Test("decir que sí importa con identificadores nuevos; decir que no, borra")
    func segunLaRespuesta() {
        #expect(AsentarCuenta.asiento(segunRespuesta: true) == .adoptarLoQueHay)
        #expect(AsentarCuenta.asiento(segunRespuesta: false) == .empezarDeCero)
    }

    @Test("el mismo correo en otro servidor es otra biblioteca")
    func mismoCorreoOtroServidor() {
        let enPruebas = AsentarCuenta.identidadDueno(
            urlServidor: "http://192.168.1.10:8090",
            email: "a@b.com"
        )
        let enElDeVerdad = AsentarCuenta.identidadDueno(
            urlServidor: "https://api.ejemplo.com",
            email: "a@b.com"
        )

        #expect(enPruebas != enElDeVerdad)
    }
}
