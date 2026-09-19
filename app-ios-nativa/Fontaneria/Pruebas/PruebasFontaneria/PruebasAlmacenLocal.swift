import Dominio
import Foundation
import Testing

@testable import Fontaneria

/// Base de datos en memoria: cada prueba estrena la suya y no deja ficheros.
private func almacenDePrueba() throws -> AlmacenLocal {
    try AlmacenLocal(ruta: ":memory:")
}

private func elemento(
    _ id: String,
    url: String = "https://a.com",
    titulo: String? = "Un título",
    etiquetas: [String] = [],
    actualizadoEn: MarcaDeTiempo = 100,
    borrado: Bool = false
) -> Elemento {
    Elemento(
        id: id,
        url: url,
        titulo: titulo,
        etiquetas: etiquetas,
        creadoEn: 50,
        actualizadoEn: actualizadoEn,
        borrado: borrado
    )
}

@Suite("Guardar y leer enlaces")
struct PruebasGuardarElementos {
    @Test("un enlace completo vuelve tal cual de la base de datos")
    func idaYVueltaCompleta() throws {
        let almacen = try almacenDePrueba()
        let original = Elemento(
            id: "e1",
            url: "https://a.com/artículo",
            titulo: "Café & teoría",
            descripcion: "Una descripción",
            imagenUrl: "https://a.com/i.jpg",
            tipo: .articulo,
            etiquetas: ["ocio", "pendiente"],
            creadoEn: 50,
            actualizadoEn: 100
        )

        try almacen.guardar([original.id: original])

        #expect(try almacen.cargarTodos()["e1"] == original)
    }

    @Test("los campos vacíos siguen vacíos, no se vuelven cadenas")
    func camposNulos() throws {
        let almacen = try almacenDePrueba()
        let pelado = Elemento(id: "e1", url: "https://a.com")

        try almacen.guardar([pelado.id: pelado])
        let leido = try almacen.cargarTodos()["e1"]

        #expect(leido?.titulo == nil)
        #expect(leido?.descripcion == nil)
        #expect(leido?.imagenUrl == nil)
        #expect(leido?.etiquetas == [])
    }

    @Test("guardar dos veces el mismo identificador actualiza, no duplica")
    func upsert() throws {
        let almacen = try almacenDePrueba()
        let primero = elemento("e1", titulo: "Viejo")
        try almacen.guardar([primero.id: primero])

        let segundo = primero.conEtiquetas(["nueva"], ahora: { 200 })
        try almacen.guardar([segundo.id: segundo])

        let todos = try almacen.cargarTodos()
        #expect(todos.count == 1)
        #expect(todos["e1"]?.etiquetas == ["nueva"])
        #expect(todos["e1"]?.actualizadoEn == 200)
    }
}

@Suite("Cola de cambios pendientes de subir")
struct PruebasColaPendientes {
    @Test("un cambio local se ve al momento y queda encolado")
    func marcarPendiente() throws {
        let almacen = try almacenDePrueba()
        let uno = elemento("e1")

        try almacen.marcarPendiente(uno)

        #expect(try almacen.cargarTodos().count == 1)
        #expect(try almacen.cargarPendientes()["e1"] == uno)
    }

    @Test("lo guardado por una sincronización no se encola")
    func guardarNoEncola() throws {
        let almacen = try almacenDePrueba()
        let uno = elemento("e1")

        try almacen.guardar([uno.id: uno])

        #expect(try almacen.cargarPendientes().isEmpty)
    }

    @Test("limpiar saca de la cola pero no borra el enlace")
    func limpiarPendientes() throws {
        let almacen = try almacenDePrueba()
        try almacen.marcarPendiente(elemento("e1"))
        try almacen.marcarPendiente(elemento("e2"))

        try almacen.limpiarPendientes(["e1"])

        #expect(try almacen.cargarPendientes().keys.sorted() == ["e2"])
        #expect(try almacen.cargarTodos().count == 2)
    }
}

@Suite("Cuenta a la que pertenece lo guardado")
struct PruebasDuenoYCursor {
    @Test("el dueño y el cursor se recuerdan; de fábrica no hay ninguno")
    func duenoYCursor() throws {
        let almacen = try almacenDePrueba()

        #expect(try almacen.duenoActual() == nil)
        #expect(try almacen.cursor() == 0)

        try almacen.fijarDueno("https://api.ejemplo.com|persona@ejemplo.com")
        try almacen.fijarCursor(1_735_000_000_000)

        #expect(try almacen.duenoActual() == "https://api.ejemplo.com|persona@ejemplo.com")
        #expect(try almacen.cursor() == 1_735_000_000_000)
    }

    @Test("contar enlaces no cuenta las lápidas")
    func contarSinLapidas() throws {
        let almacen = try almacenDePrueba()
        try almacen.guardar([
            "e1": elemento("e1"),
            "e2": elemento("e2", borrado: true),
        ])

        #expect(try almacen.contarElementos() == 1)
    }

    @Test("vaciar se lo lleva todo, incluidos el cursor y el dueño")
    func vaciar() throws {
        let almacen = try almacenDePrueba()
        try almacen.marcarPendiente(elemento("e1"))
        try almacen.marcarEtiquetaPendiente(EtiquetaDefinida(id: "t1", nombre: "ocio"))
        try almacen.fijarDueno("alguien")
        try almacen.fijarCursor(500)

        try almacen.vaciar()

        #expect(try almacen.cargarTodos().isEmpty)
        #expect(try almacen.cargarPendientes().isEmpty)
        #expect(try almacen.cargarEtiquetasDefinidas().isEmpty)
        #expect(try almacen.cargarEtiquetasPendientes().isEmpty)
        #expect(try almacen.duenoActual() == nil)
        #expect(try almacen.cursor() == 0)
    }
}

@Suite("Adoptar lo que había al entrar en otra cuenta")
struct PruebasAdoptar {
    @Test("todo cambia de identificador y queda listo para subir")
    func identificadoresNuevos() throws {
        let almacen = try almacenDePrueba()
        try almacen.guardar(["e1": elemento("e1"), "e2": elemento("e2")])
        try almacen.guardarEtiquetasDefinidas(["t1": EtiquetaDefinida(id: "t1", nombre: "ocio")])

        var contador = 0
        try almacen.adoptarConIdsNuevos(generarId: {
            contador += 1
            return "nuevo-\(contador)"
        })

        let todos = try almacen.cargarTodos()
        #expect(todos.count == 2)
        #expect(todos.keys.allSatisfy { $0.hasPrefix("nuevo-") })
        // Si no entran en la cola, la cuenta nueva se queda sin ellos.
        #expect(try almacen.cargarPendientes().count == 2)
        #expect(try almacen.cargarEtiquetasPendientes().count == 1)
    }

    @Test("las lápidas se tiran: son el borrado de otra cuenta")
    func lapidasFuera() throws {
        let almacen = try almacenDePrueba()
        try almacen.guardar([
            "e1": elemento("e1"),
            "e2": elemento("e2", borrado: true),
        ])

        try almacen.adoptarConIdsNuevos()

        #expect(try almacen.cargarTodos().count == 1)
    }
}

@Suite("Etiquetas reservadas en la base de datos")
struct PruebasEtiquetasEnAlmacen {
    @Test("una etiqueta vuelve tal cual, y su cola funciona igual que la de enlaces")
    func idaYVueltaYCola() throws {
        let almacen = try almacenDePrueba()
        let etiqueta = EtiquetaDefinida(
            id: "t1",
            nombre: "ocio",
            creadoEn: 50,
            actualizadoEn: 100
        )

        try almacen.marcarEtiquetaPendiente(etiqueta)

        #expect(try almacen.cargarEtiquetasDefinidas()["t1"] == etiqueta)
        #expect(try almacen.cargarEtiquetasPendientes()["t1"] == etiqueta)

        try almacen.limpiarEtiquetasPendientes(["t1"])

        #expect(try almacen.cargarEtiquetasPendientes().isEmpty)
        #expect(try almacen.cargarEtiquetasDefinidas().count == 1)
    }
}
