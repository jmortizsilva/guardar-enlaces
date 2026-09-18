import XCTest

/// Pruebas contra el árbol de accesibilidad de verdad: lo que estas leen es
/// lo mismo que lee VoiceOver.
///
/// Lo que NO cubren, y hay que seguir comprobando a mano en un teléfono:
/// las acciones del rotor. `accessibilityCustomActions` no se puede enumerar
/// desde aquí, así que que el rotor ofrezca Copiar URL, Abrir en Safari,
/// Editar etiquetas y Eliminar sigue siendo una comprobación de oído. Lo que
/// sí se comprueba es que esas mismas acciones existen al deslizar la fila,
/// que usan los mismos textos.
@MainActor
final class PruebasListaEnlaces: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-pruebas-de-interfaz"]
        app.launch()
    }

    private func fila(queEmpiezaPor titulo: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", titulo)).firstMatch
    }

    func testLaListaEnseniaLoGuardadoYNoLasLapidas() {
        XCTAssertTrue(
            fila(queEmpiezaPor: "Las mejores alternativas a Pocket").waitForExistence(timeout: 5))
        XCTAssertTrue(fila(queEmpiezaPor: "Guía de Swift Testing").exists)
        XCTAssertTrue(fila(queEmpiezaPor: "Cómo funciona VoiceOver en iOS").exists)
        // Un enlace eliminado sigue en la base de datos como lápida, para que
        // los demás dispositivos se enteren, pero no se enseña.
        XCTAssertFalse(fila(queEmpiezaPor: "Este se eliminó").exists)
    }

    func testCadaFilaSeLeeEnteraDeUnaVez() {
        let fila = fila(queEmpiezaPor: "Las mejores alternativas a Pocket")
        XCTAssertTrue(fila.waitForExistence(timeout: 5))

        // Título, de dónde es, sus etiquetas y cuándo, en una sola etiqueta:
        // es como quedó decidido que se oiga.
        //
        // Se comprueba el principio EXACTO, con su punto entre el título y el
        // resto. Con un `contains` suelto, quitar la etiqueta accesible no
        // rompería nada: SwiftUI compondría una parecida juntando los dos
        // textos con una coma, y la prueba seguiría pasando sin proteger nada.
        XCTAssertTrue(
            fila.label.hasPrefix(
                "Las mejores alternativas a Pocket. www.xataka.com — ocio, pendiente — "),
            "la etiqueta era: \(fila.label)"
        )
        // La fecha, con el mes en letra: «15/3/2024» se lee dígito a dígito.
        XCTAssertTrue(fila.label.contains("de marzo de 2024"), "la fecha era: \(fila.label)")
    }

    func testLosBotonesDeLaBarraDicenQueHacen() {
        XCTAssertTrue(app.buttons["Añadir enlace"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Ajustes"].exists)
        // El filtro dice por dónde está filtrando ahora mismo.
        XCTAssertTrue(app.buttons["Filtrar por etiqueta: Todas"].exists)
    }

    func testBuscarDejaSoloLoQueCoincide() {
        let busqueda = app.searchFields["Título, URL o etiqueta"]
        XCTAssertTrue(busqueda.waitForExistence(timeout: 5))

        busqueda.tap()
        busqueda.typeText("swift")

        XCTAssertTrue(fila(queEmpiezaPor: "Guía de Swift Testing").waitForExistence(timeout: 3))
        XCTAssertFalse(fila(queEmpiezaPor: "Las mejores alternativas a Pocket").exists)
    }

    func testCuandoLaBusquedaNoEncuentraNadaLoDiceConLoQueSeBusco() {
        let busqueda = app.searchFields["Título, URL o etiqueta"]
        XCTAssertTrue(busqueda.waitForExistence(timeout: 5))

        busqueda.tap()
        busqueda.typeText("zzzz")

        // Decir «no hay enlaces guardados» con una búsqueda puesta haría
        // dudar de si se ha perdido algo.
        XCTAssertTrue(
            app.staticTexts["Ningún enlace con «zzzz»."].waitForExistence(timeout: 3)
        )
    }

    func testAlEliminarSeAvisaDeQueNoHayCuentaYElEnlaceSeVa() {
        let fila = fila(queEmpiezaPor: "Guía de Swift Testing")
        XCTAssertTrue(fila.waitForExistence(timeout: 5))

        fila.swipeLeft()
        app.buttons["Eliminar"].firstMatch.tap()

        // El diálogo dice qué se elimina y qué va a pasar con ello.
        XCTAssertTrue(
            app.alerts["¿Eliminar «Guía de Swift Testing»?"].waitForExistence(timeout: 3)
        )
        XCTAssertTrue(
            app.staticTexts["Está guardado solo en este iPhone."].exists,
            "sin sesión iniciada, el enlace solo está aquí y el diálogo tiene que decirlo"
        )

        app.alerts.buttons["Eliminar"].tap()

        XCTAssertFalse(fila.waitForExistence(timeout: 3))
    }

    func testDeslizarUnaFilaOfreceEditarEtiquetas() {
        let fila = fila(queEmpiezaPor: "Guía de Swift Testing")
        XCTAssertTrue(fila.waitForExistence(timeout: 5))

        fila.swipeRight()
        app.buttons["Editar etiquetas"].firstMatch.tap()

        XCTAssertTrue(app.navigationBars["Editar etiquetas"].waitForExistence(timeout: 3))
        // Las que ya lleva el enlace y las que existen en la biblioteca.
        XCTAssertTrue(app.buttons["trabajo"].exists)
        XCTAssertTrue(app.buttons["ocio"].exists)
        XCTAssertTrue(app.textFields["Nueva etiqueta"].exists)
    }
}
