import Foundation
import Testing

@testable import Dominio

private func nuevo(
    _ url: String,
    titulo: String? = nil,
    etiquetas: [String] = [],
    en instante: MarcaDeTiempo = 100,
    generarId: GeneradorId
) -> Elemento {
    nuevoElementoLocal(
        DatosElementoNuevo(url: url, titulo: titulo, etiquetas: etiquetas),
        ahora: relojFijo(instante),
        generarId: generarId
    )
}

@Suite("Aplicar lo que baja del servidor")
struct PruebasAplicarPull {
    @Test("lo que no estaba se añade")
    func anadeLoNuevo() {
        let recibido = nuevo("https://a.com", generarId: generadorSecuencial())

        let cache = Sincronizacion.aplicarPull([:], recibidos: [recibido], pendientes: [:])

        #expect(cache[recibido.id] == recibido)
    }

    @Test("no pisa un cambio local que todavía está sin subir y es más nuevo")
    func respetaLoPendienteMasNuevo() {
        let base = nuevo("https://a.com", titulo: "Original", generarId: generadorSecuencial())
        let pendiente = base.conEtiquetas(["editado aquí"], ahora: relojFijo(300))
        let delServidor = base.conEtiquetas(["versión vieja del servidor"], ahora: relojFijo(200))

        let cache = Sincronizacion.aplicarPull(
            [base.id: base],
            recibidos: [delServidor],
            pendientes: [base.id: pendiente]
        )

        #expect(cache[base.id] == base)
    }

    @Test("si lo que baja es más nuevo que lo pendiente, sí se aplica")
    func aplicaLoMasNuevo() {
        let base = nuevo("https://a.com", titulo: "Original", generarId: generadorSecuencial())
        let pendiente = base.conEtiquetas(["editado aquí"], ahora: relojFijo(150))
        let delServidor = base.conEtiquetas(["ya venía de otro sitio"], ahora: relojFijo(500))

        let cache = Sincronizacion.aplicarPull(
            [base.id: base],
            recibidos: [delServidor],
            pendientes: [base.id: pendiente]
        )

        #expect(cache[base.id] == delServidor)
    }
}

@Suite("Aplicar la respuesta de lo que se sube")
struct PruebasAplicarPush {
    @Test("manda la versión del servidor, aunque sea más antigua")
    func mandaElServidor() {
        let local = nuevo("https://a.com", titulo: "Mío", generarId: generadorSecuencial())
        let definitivo = local.conEtiquetas(["el servidor ganó"], ahora: relojFijo(50))

        let cache = Sincronizacion.aplicarRespuestaPush(
            [local.id: local], definitivos: [definitivo])

        #expect(cache[local.id] == definitivo)
    }
}

@Suite("Qué se ve en la lista")
struct PruebasElementosVisibles {
    @Test("oculta los borrados y pone lo guardado más recientemente primero")
    func ordenYBorrados() {
        let generar = generadorSecuencial()
        let a = nuevo("https://a.com", en: 100, generarId: generar)
        let b = nuevo("https://b.com", en: 200, generarId: generar)
        let c = nuevo("https://c.com", en: 300, generarId: generar)
            .marcadoComoBorrado(ahora: relojFijo(400))

        let visibles = Sincronizacion.elementosVisibles([a.id: a, b.id: b, c.id: c])

        #expect(visibles.map(\.id) == [b.id, a.id])
    }

    @Test("retocar un enlace viejo no lo manda al principio de la lista")
    func retocarNoReordena() {
        let generar = generadorSecuencial()
        let viejo = nuevo("https://viejo.com", en: 100, generarId: generar)
        let nuevoEnlace = nuevo("https://nuevo.com", en: 200, generarId: generar)
        // Se le cambia una etiqueta al viejo: su fecha de modificación pasa a
        // ser la más alta de las dos, pero se guardó antes y ahí sigue.
        let viejoRetocado = viejo.conEtiquetas(["ocio"], ahora: relojFijo(900))

        let visibles = Sincronizacion.elementosVisibles([
            viejoRetocado.id: viejoRetocado, nuevoEnlace.id: nuevoEnlace,
        ])

        #expect(visibles.map(\.id) == [nuevoEnlace.id, viejoRetocado.id])
    }

    @Test("dos guardados en el mismo instante salen siempre en el mismo orden")
    func ordenEstable() {
        // Importar una biblioteca entera pone la misma fecha a todo. Sin
        // desempate, el diccionario los devolvería en cualquier orden y la
        // lista se recolocaría sola entre dos aperturas.
        let generar = generadorSecuencial()
        let a = nuevo("https://a.com", en: 100, generarId: generar)
        let b = nuevo("https://b.com", en: 100, generarId: generar)
        let cache = [a.id: a, b.id: b]

        let primero = Sincronizacion.elementosVisibles(cache).map(\.id)
        let segundo = Sincronizacion.elementosVisibles(cache).map(\.id)

        #expect(primero == segundo)
    }
}

@Suite("Etiquetas reservadas al sincronizar")
struct PruebasSincronizacionEtiquetas {
    @Test("no pisa una etiqueta local sin subir que es más nueva")
    func respetaLoPendiente() {
        let base = nuevaEtiquetaDefinida(
            nombre: "ocio",
            ahora: relojFijo(100),
            generarId: generadorSecuencial()
        )
        let pendiente = base.renombrada("tiempo libre", ahora: relojFijo(300))
        let delServidor = base.renombrada("ocio", ahora: relojFijo(200))

        let cache = Sincronizacion.aplicarPullEtiquetas(
            [base.id: base],
            recibidas: [delServidor],
            pendientes: [base.id: pendiente]
        )

        #expect(cache[base.id] == base)
    }

    @Test("las borradas no se ofrecen, y las demás salen por nombre")
    func visiblesOrdenadas() {
        let generar = generadorSecuencial()
        let ocio = nuevaEtiquetaDefinida(nombre: "ocio", generarId: generar)
        let casa = nuevaEtiquetaDefinida(nombre: "casa", generarId: generar)
        let ida = nuevaEtiquetaDefinida(nombre: "ida", generarId: generar)
            .marcadaComoBorrada()

        let visibles = Sincronizacion.etiquetasReservadasVisibles([
            ocio.id: ocio, casa.id: casa, ida.id: ida,
        ])

        #expect(visibles.map(\.nombre) == ["casa", "ocio"])
    }
}

@Suite("Cuándo toca sincronizar")
struct PruebasTocaSincronizar {
    @Test("sin ninguna sincronización previa, toca")
    func primeraVez() {
        // El cero no es «hace un instante»: es 1970.
        #expect(Sincronizacion.tocaSincronizar(ultima: 0, ahora: relojDelSistema()))
    }

    @Test("volver a la app dos veces seguidas no lanza dos sincronizaciones")
    func frenoEntreDos() {
        #expect(!Sincronizacion.tocaSincronizar(ultima: 1000, ahora: 2000, intervalo: 30_000))
    }

    @Test("pasado el intervalo vuelve a tocar")
    func pasadoElIntervalo() {
        #expect(Sincronizacion.tocaSincronizar(ultima: 1000, ahora: 31_000, intervalo: 30_000))
    }
}
