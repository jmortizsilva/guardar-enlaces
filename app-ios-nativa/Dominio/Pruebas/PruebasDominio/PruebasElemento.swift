import Foundation
import Testing

@testable import Dominio

@Suite("Elemento nuevo")
struct PruebasElementoNuevo {
    @Test("toma el identificador y las fechas de lo que se le inyecta")
    func idYFechasInyectados() {
        let elemento = nuevoElementoLocal(
            DatosElementoNuevo(url: "https://a.com", titulo: "A"),
            ahora: relojFijo(1000),
            generarId: generadorSecuencial()
        )

        #expect(elemento.url == "https://a.com")
        #expect(elemento.titulo == "A")
        #expect(elemento.creadoEn == 1000)
        #expect(elemento.actualizadoEn == 1000)
        #expect(elemento.borrado == false)
        #expect(elemento.id == "id-de-prueba-1")
    }

    @Test("dos elementos no comparten identificador")
    func identificadoresDistintos() {
        let generar = generadorSecuencial()
        let a = nuevoElementoLocal(DatosElementoNuevo(url: "https://a.com"), generarId: generar)
        let b = nuevoElementoLocal(DatosElementoNuevo(url: "https://b.com"), generarId: generar)

        #expect(a.id != b.id)
    }

    @Test("el identificador de verdad va en minúsculas, como en los otros dos clientes")
    func identificadorEnMinusculas() {
        let identificador = generarIdUnico()

        #expect(identificador == identificador.lowercased())
        #expect(identificador.count == 36)
    }
}

@Suite("Cambios sobre un elemento")
struct PruebasCambiosElemento {
    private func elementoDePrueba(_ instante: MarcaDeTiempo = 100) -> Elemento {
        nuevoElementoLocal(
            DatosElementoNuevo(url: "https://a.com", titulo: "Viejo"),
            ahora: relojFijo(instante),
            generarId: generadorSecuencial()
        )
    }

    @Test("borrar deja una lápida, no tira los datos")
    func borrarDejaLapida() {
        let borrado = elementoDePrueba().marcadoComoBorrado(ahora: relojFijo(200))

        #expect(borrado.borrado)
        #expect(borrado.actualizadoEn == 200)
        #expect(borrado.url == "https://a.com")
        #expect(borrado.titulo == "Viejo")
    }

    @Test("cambiar las etiquetas mueve la fecha y respeta el identificador")
    func cambiarEtiquetas() {
        let original = elementoDePrueba()
        let editado = original.conEtiquetas(["ocio"], ahora: relojFijo(200))

        #expect(editado.etiquetas == ["ocio"])
        #expect(editado.actualizadoEn == 200)
        #expect(editado.id == original.id)
        #expect(editado.creadoEn == original.creadoEn)
    }

    @Test("los metadatos se pueden completar después de guardar")
    func completarMetadatos() {
        let editado = elementoDePrueba().conMetadatos(
            titulo: "Nuevo",
            descripcion: "Una descripción",
            imagenUrl: "https://a.com/i.jpg",
            tipo: .articulo,
            ahora: relojFijo(200)
        )

        #expect(editado.titulo == "Nuevo")
        #expect(editado.tipo == .articulo)
        #expect(editado.actualizadoEn == 200)
    }
}

@Suite("Elemento y el JSON del contrato")
struct PruebasElementoJson {
    @Test("ir al JSON y volver no pierde nada")
    func idaYVueltaCompleta() throws {
        let original = nuevoElementoLocal(
            DatosElementoNuevo(
                url: "https://a.com",
                titulo: "A",
                descripcion: "d",
                imagenUrl: "https://a.com/i.jpg",
                tipo: .video,
                etiquetas: ["ocio", "pendiente"]
            ),
            ahora: relojFijo(100),
            generarId: generadorSecuencial()
        )

        #expect(try idaYVuelta(original) == original)
    }

    @Test("una baja que solo trae identificador y fecha se entiende igual")
    func camposAusentes() throws {
        let elemento: Elemento = try desdeJson(
            #"{"id": "x1", "actualizadoEn": 5, "borrado": true}"#
        )

        #expect(elemento.url == "")
        #expect(elemento.titulo == nil)
        #expect(elemento.etiquetas == [])
        #expect(elemento.tipo == .enlace)
        #expect(elemento.creadoEn == 0)
        #expect(elemento.borrado)
    }

    @Test("un tipo que esta versión no conoce no tira el enlace")
    func tipoDesconocido() throws {
        let elemento: Elemento = try desdeJson(
            #"{"id": "x1", "url": "https://a.com", "tipo": "pódcast"}"#
        )

        #expect(elemento.tipo == .enlace)
        #expect(elemento.url == "https://a.com")
    }

    @Test("sin identificador no hay elemento que valga")
    func sinIdentificador() {
        #expect(throws: (any Error).self) {
            let _: Elemento = try desdeJson(#"{"url": "https://a.com"}"#)
        }
    }
}

@Suite("Etiqueta reservada")
struct PruebasEtiquetaDefinida {
    @Test("nace con nombre, identificador y las dos fechas iguales")
    func nace() {
        let etiqueta = nuevaEtiquetaDefinida(
            nombre: "ocio",
            ahora: relojFijo(100),
            generarId: generadorSecuencial()
        )

        #expect(etiqueta.nombre == "ocio")
        #expect(etiqueta.id == "id-de-prueba-1")
        #expect(etiqueta.creadoEn == 100)
        #expect(etiqueta.actualizadoEn == 100)
        #expect(etiqueta.borrado == false)
    }

    @Test("renombrar y borrar mueven la fecha, no el identificador")
    func renombrarYBorrar() {
        let original = nuevaEtiquetaDefinida(
            nombre: "ocio",
            ahora: relojFijo(100),
            generarId: generadorSecuencial()
        )

        let renombrada = original.renombrada("tiempo libre", ahora: relojFijo(200))
        #expect(renombrada.nombre == "tiempo libre")
        #expect(renombrada.actualizadoEn == 200)
        #expect(renombrada.id == original.id)

        let borrada = renombrada.marcadaComoBorrada(ahora: relojFijo(300))
        #expect(borrada.borrado)
        #expect(borrada.nombre == "tiempo libre")
        #expect(borrada.actualizadoEn == 300)
    }

    @Test("ir al JSON y volver no pierde nada")
    func idaYVueltaCompleta() throws {
        let original = nuevaEtiquetaDefinida(
            nombre: "ocio",
            ahora: relojFijo(100),
            generarId: generadorSecuencial()
        )

        #expect(try idaYVuelta(original) == original)
    }

    @Test("una baja que solo trae identificador y fecha se entiende igual")
    func camposAusentes() throws {
        let etiqueta: EtiquetaDefinida = try desdeJson(
            #"{"id": "t1", "actualizadoEn": 5, "borrado": true}"#
        )

        #expect(etiqueta.nombre == "")
        #expect(etiqueta.borrado)
    }
}
