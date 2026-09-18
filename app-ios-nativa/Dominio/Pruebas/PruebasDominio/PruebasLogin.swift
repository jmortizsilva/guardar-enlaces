import Foundation
import Testing

@testable import Dominio

@Suite("La vuelta del inicio de sesión")
struct PruebasLeerCallback {
    private func leer(_ texto: String) -> Login.Resultado {
        Login.leerCallback(URL(string: texto)!)
    }

    @Test("con código, es un inicio de sesión que fue bien")
    func conCodigo() {
        #expect(
            leer("guardalonativo://auth-callback?codigo=abc123")
                == .exito(codigoDeCanje: "abc123")
        )
    }

    @Test("sin correo, lo dice con sus palabras y no con el motivo del contrato")
    func sinEmail() {
        #expect(
            leer("guardalonativo://auth-callback?error=sin_email")
                == .error(
                    mensaje:
                        "Tu cuenta no ha dado ningún correo, y hace falta para crear la cuenta."
                )
        )
    }

    @Test("si el proveedor rechazó el intercambio, invita a repetir")
    func falloIntercambio() {
        #expect(
            leer("guardalonativo://auth-callback?error=fallo_intercambio")
                == .error(mensaje: "Google rechazó el inicio de sesión. Vuelve a intentarlo.")
        )
    }

    @Test("un motivo que no se conoce no deja al usuario sin explicación")
    func motivoDesconocido() {
        #expect(
            leer("guardalonativo://auth-callback?error=algo_nuevo")
                == .error(mensaje: "No se pudo iniciar sesión.")
        )
    }

    @Test("una vuelta sin nada tampoco se da por buena")
    func sinParametros() {
        #expect(
            leer("guardalonativo://auth-callback") == .error(mensaje: "No se pudo iniciar sesión."))
    }

    @Test("un código vacío no cuenta como haber entrado")
    func codigoVacio() {
        #expect(
            leer("guardalonativo://auth-callback?codigo=")
                == .error(mensaje: "No se pudo iniciar sesión."))
    }
}

@Suite("El estado del inicio de sesión")
struct PruebasEstadoLogin {
    @Test("son 32 caracteres hexadecimales")
    func formato() {
        let estado = Login.generarEstado()

        #expect(estado.count == 32)
        #expect(estado.allSatisfy { $0.isHexDigit })
    }

    @Test("dos seguidos no se repiten")
    func noSeRepite() {
        #expect(Login.generarEstado() != Login.generarEstado())
    }

    @Test("se puede fijar para probarlo")
    func inyectable() {
        #expect(
            Login.generarEstado(aleatorio: { cuantos in Array(repeating: 0xAB, count: cuantos) })
                == String(repeating: "ab", count: 16))
    }
}

@Suite("Textos de ajustes y de entrar")
struct PruebasTextosAjustes {
    @Test("el singular y el plural de los enlaces que ya había")
    func preguntaImportar() {
        #expect(
            Textos.preguntaImportar(cuantos: 1, deOtraCuenta: false)
                .hasPrefix("Hay 1 enlace guardado en este iPhone sin cuenta.")
        )
        #expect(
            Textos.preguntaImportar(cuantos: 3, deOtraCuenta: true)
                .hasPrefix("Hay 3 enlaces guardados en este iPhone con otra cuenta.")
        )
    }

    @Test("la pregunta dice que borrar no se puede deshacer")
    func avisaDeLoIrreversible() {
        #expect(
            Textos.preguntaImportar(cuantos: 2, deOtraCuenta: false).contains(
                "no se pueden recuperar"))
    }

    @Test("con sesión iniciada se dice con qué cuenta")
    func conCuenta() {
        #expect(
            Textos.sesionIniciadaComo(email: "persona@ejemplo.com")
                == "Sesión iniciada como persona@ejemplo.com. Tus enlaces se sincronizan con el PC."
        )
    }
}
