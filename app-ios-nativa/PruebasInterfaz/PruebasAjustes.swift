import XCTest

/// Lo que se puede comprobar sin iniciar sesión de verdad. El inicio de
/// sesión en sí abre la hoja del sistema con Google y se queda fuera de
/// aquí: eso hay que probarlo a mano.
@MainActor
final class PruebasAjustes: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-pruebas-de-interfaz"]
        app.launch()
        app.buttons["Ajustes"].tap()
        XCTAssertTrue(app.navigationBars["Ajustes"].waitForExistence(timeout: 5))
    }

    func testSinCuentaDiceDondeEstanLosEnlaces() {
        XCTAssertTrue(
            app.staticTexts["Sin cuenta: los enlaces se guardan solo en este iPhone."].exists
        )
        XCTAssertTrue(app.buttons["Entrar con Google"].exists)
        // Sin sesión no hay nada que cerrar.
        XCTAssertFalse(app.buttons["Cerrar sesión"].exists)
    }

    func testDiceQueVersionTienesInstalada() {
        // Ocupa el sitio del antiguo «Buscar actualizaciones»: sin
        // actualizaciones por aire no hay nada que buscar, pero sí hace falta
        // saber qué build llegó por TestFlight.
        let version = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH 'Versión '")
        ).firstMatch
        XCTAssertTrue(version.exists, "no se ve la versión instalada")
    }

    func testEntrarExplicaParaQueSirveLaCuentaYSePuedeDejarParaLuego() {
        app.buttons["Entrar con Google"].tap()

        XCTAssertTrue(app.navigationBars["Entrar con una cuenta"].waitForExistence(timeout: 3))
        XCTAssertTrue(
            app.staticTexts.containing(
                NSPredicate(
                    format: "label BEGINSWITH 'La cuenta sirve para tener los mismos enlaces'")
            ).firstMatch.exists
        )

        app.buttons["Ahora no"].tap()

        // Vuelve a Ajustes, no se queda en ningún sitio raro.
        XCTAssertTrue(app.navigationBars["Ajustes"].waitForExistence(timeout: 3))
    }

    func testCerrarVuelveALaLista() {
        app.buttons["Cerrar"].tap()

        XCTAssertTrue(app.navigationBars["Guárdalo"].waitForExistence(timeout: 3))
    }
}
