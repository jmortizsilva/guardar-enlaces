import XCTest

@MainActor
final class PruebasDetalle: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-pruebas-de-interfaz"]
        app.launch()
    }

    private func abrirDetalleDe(_ titulo: String) {
        let fila = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", titulo))
            .firstMatch
        XCTAssertTrue(fila.waitForExistence(timeout: 5))
        fila.press(forDuration: 1.2)
        app.buttons["Ver detalles"].firstMatch.tap()
    }

    func testElDetalleEnseniaCadaCosaPorSeparado() {
        abrirDetalleDe("Las mejores alternativas a Pocket")

        XCTAssertTrue(
            app.navigationBars["Las mejores alternativas a Pocket"].waitForExistence(timeout: 3))
        // En la lista todo esto se oye de corrido dentro de la fila; aquí
        // cada cosa está en su sitio y se puede recorrer.
        XCTAssertTrue(
            app.staticTexts["URL: https://www.xataka.com/basics/alternativas-pocket"].exists
        )
        XCTAssertTrue(
            app.staticTexts["Descripción: Pocket cierra y estas son las opciones"].exists
        )
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Guardado el '"))
                .firstMatch.exists
        )
    }

    func testEliminarDesdeElDetalleAvisaYCierra() {
        abrirDetalleDe("Guía de Swift Testing")
        XCTAssertTrue(app.navigationBars["Guía de Swift Testing"].waitForExistence(timeout: 3))

        app.buttons["Eliminar"].firstMatch.tap()
        XCTAssertTrue(app.alerts["¿Eliminar «Guía de Swift Testing»?"].waitForExistence(timeout: 3))
        app.alerts.buttons["Eliminar"].tap()

        // Quedarse en la ficha de algo que ya no existe deja a VoiceOver
        // leyendo un fantasma.
        XCTAssertTrue(app.navigationBars["Guárdalo"].waitForExistence(timeout: 5))
        XCTAssertFalse(
            app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Guía de Swift Testing'"))
                .firstMatch.exists
        )
    }
}

@MainActor
final class PruebasGestionEtiquetas: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-pruebas-de-interfaz"]
        app.launch()
        app.buttons["Filtrar por etiqueta: Todas"].tap()
        app.buttons["Gestionar etiquetas"].tap()
        XCTAssertTrue(app.navigationBars["Gestionar etiquetas"].waitForExistence(timeout: 5))
    }

    func testCadaEtiquetaDiceCuantosEnlacesLleva() {
        XCTAssertTrue(app.staticTexts["ocio, 1 enlace"].exists)
        XCTAssertTrue(app.staticTexts["pendiente, 2 enlaces"].exists)
        XCTAssertTrue(app.staticTexts["trabajo, 1 enlace"].exists)
    }

    func testCrearUnaEtiquetaQueTodaviaNoLlevaNingunEnlace() {
        let campo = app.textFields["Nueva etiqueta"]
        campo.tap()
        campo.typeText("recetas")
        app.buttons["Crear etiqueta"].tap()

        // Existe aunque no la lleve nadie: para eso están las reservadas.
        XCTAssertTrue(app.staticTexts["recetas, ningún enlace"].waitForExistence(timeout: 3))
    }

    func testAntesDeEliminarDiceACuantosEnlacesAfecta() {
        app.staticTexts["pendiente, 2 enlaces"].press(forDuration: 1.2)
        app.buttons["Eliminar"].firstMatch.tap()

        XCTAssertTrue(
            app.staticTexts[
                "¿Eliminar la etiqueta «pendiente»? Se quitará de 2 enlaces y no se puede deshacer."
            ].waitForExistence(timeout: 3)
        )

        app.alerts.buttons["Eliminar"].tap()

        XCTAssertFalse(app.staticTexts["pendiente, 2 enlaces"].waitForExistence(timeout: 3))
    }
}
