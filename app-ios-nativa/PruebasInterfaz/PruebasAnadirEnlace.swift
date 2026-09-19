import XCTest

/// Las direcciones de prueba usan el dominio `.invalid`, que por norma no
/// resuelve nunca: así la comprobación falla al instante y estas pruebas no
/// dependen de que haya red ni de que un sitio real conteste.
@MainActor
final class PruebasAnadirEnlace: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-pruebas-de-interfaz"]
        app.launch()
        app.buttons["Añadir enlace"].tap()
        XCTAssertTrue(app.navigationBars["Añadir enlace"].waitForExistence(timeout: 5))
    }

    private var campoUrl: XCUIElement {
        app.textFields["URL del enlace"]
    }

    func testUnaDireccionQueNoValeLoDiceYNoDejaGuardar() {
        campoUrl.tap()
        campoUrl.typeText("esto no es una url")

        XCTAssertTrue(
            app.staticTexts["Escribe una dirección que empiece por http:// o https://"]
                .waitForExistence(timeout: 3)
        )
        XCTAssertFalse(app.buttons["Guardar"].isEnabled)
    }

    func testGuardarNoEsperaALaComprobacion() {
        campoUrl.tap()
        campoUrl.typeText("https://nuevo.invalid/articulo")

        // El botón funciona en cuanto la dirección es válida: sin esto, sin
        // cobertura no se podría guardar nada, que es cuando más prisa hay.
        XCTAssertTrue(app.buttons["Guardar"].isEnabled)

        app.buttons["Guardar"].tap()

        // La comprobación no llega a ningún sitio, así que el enlace se
        // guarda con la dirección por título, pero se guarda.
        let fila = app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH %@", "https://nuevo.invalid/articulo"))
            .firstMatch
        XCTAssertTrue(fila.waitForExistence(timeout: 15))
    }

    func testUnaUrlQueYaTienesOfreceActualizarEnVezDeGuardar() {
        campoUrl.tap()
        // La misma página que el primer enlace de ejemplo, escrita de otra
        // forma: sin www, con http y arrastrando un parámetro de seguimiento.
        campoUrl.typeText("http://xataka.com/basics/alternativas-pocket/?utm_source=twitter")

        XCTAssertTrue(app.buttons["Actualizar"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["Guardar"].exists)
        XCTAssertTrue(
            app.staticTexts.containing(
                NSPredicate(format: "label BEGINSWITH 'Ya tienes guardado este enlace'")
            ).firstMatch.exists
        )
    }

    func testActualizarNoCreaOtroEnlaceYSumaLasEtiquetas() {
        campoUrl.tap()
        campoUrl.typeText("https://www.swift.org/documentation/testing/")
        XCTAssertTrue(app.buttons["Actualizar"].waitForExistence(timeout: 3))

        app.buttons["Etiquetas: ninguna"].tap()
        XCTAssertTrue(app.navigationBars["Editar etiquetas"].waitForExistence(timeout: 3))
        app.buttons["ocio"].tap()
        app.buttons["Guardar"].tap()

        XCTAssertTrue(app.buttons["Etiquetas: ocio"].waitForExistence(timeout: 3))
        app.buttons["Actualizar"].tap()

        // Granular a propósito: si algo falla aquí, el mensaje tiene que
        // decir en qué paso se quedó, no solo que no encontró la fila.
        XCTAssertTrue(
            app.navigationBars["Guárdalo"].waitForExistence(timeout: 10),
            "la hoja de añadir no se cerró al pulsar Actualizar"
        )

        let filas = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Guía de Swift Testing")
        )
        XCTAssertTrue(
            filas.firstMatch.waitForExistence(timeout: 5), "el enlace no está en la lista")
        // Ni se duplica el enlace...
        XCTAssertEqual(filas.count, 1)
        // ...ni se pierde la que ya llevaba. El orden es el de siempre:
        // primero las que tenía, y detrás las nuevas.
        XCTAssertTrue(
            filas.firstMatch.label.contains("trabajo, ocio"),
            "las etiquetas quedaron: \(filas.firstMatch.label)"
        )
    }
}
