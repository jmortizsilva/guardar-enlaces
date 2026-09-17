import Foundation
import Testing

@testable import Dominio

@Suite("Leer los metadatos de una página")
struct PruebasExtraerMetadatos {
    @Test("prefiere og:title al título de la pestaña")
    func prefiereOpenGraph() {
        let html = """
            <html><head>
            <title>El de la pestaña</title>
            <meta property="og:title" content="Café &amp; teoría">
            <meta property="og:description" content="Una descripción">
            <meta content="https://ejemplo.com/foto.jpg" property="og:image">
            <meta property="og:type" content="article">
            </head></html>
            """

        #expect(
            Metadatos.extraer(de: html)
                == MetadatosExtraidos(
                    titulo: "Café & teoría",
                    descripcion: "Una descripción",
                    imagenUrl: "https://ejemplo.com/foto.jpg",
                    tipo: .articulo
                )
        )
    }

    @Test("sin og:title cae al título de la pestaña, sin espacios de sobra")
    func caeAlTituloDeLaPestana() {
        let html = "<html><head><title>  Solo esto  </title></head></html>"

        #expect(Metadatos.extraer(de: html) == MetadatosExtraidos(titulo: "Solo esto"))
    }

    @Test("una página sin nada no rompe: todo vacío y tipo enlace")
    func paginaSinNada() {
        #expect(Metadatos.extraer(de: "<html></html>") == MetadatosExtraidos())
    }

    @Test("el tipo sale de og:type, y lo que no se reconozca es un enlace")
    func tipos() {
        func tipoDe(_ ogType: String) -> TipoElemento {
            Metadatos.extraer(de: "<meta property=\"og:type\" content=\"\(ogType)\">").tipo
        }

        #expect(tipoDe("video.other") == .video)
        #expect(tipoDe("article") == .articulo)
        #expect(tipoDe("image") == .imagen)
        #expect(tipoDe("website") == .enlace)
    }

    @Test("solo se traducen las cinco entidades que traduce el backend")
    func entidadesComoElBackend() {
        // Esto no es un descuido: el servidor resuelve los metadatos cuando
        // hay cuenta y el teléfono cuando no la hay. Traducir aquí más
        // entidades que allí haría que el mismo enlace se viera distinto
        // según quién lo resolvió. Se arregla en los dos sitios o en ninguno.
        let html = "<meta property=\"og:title\" content=\"Caf&eacute; &amp; teor&iacute;a\">"

        #expect(Metadatos.extraer(de: html).titulo == "Caf&eacute; & teor&iacute;a")
    }
}

@Suite("YouTube")
struct PruebasYoutube {
    @Test("reconoce sus dominios, incluidos youtu.be y el móvil")
    func dominios() {
        #expect(Youtube.esUrlDeYoutube("https://www.youtube.com/watch?v=abc"))
        #expect(Youtube.esUrlDeYoutube("https://youtu.be/abc"))
        #expect(Youtube.esUrlDeYoutube("https://m.youtube.com/watch?v=abc"))
        #expect(!Youtube.esUrlDeYoutube("https://vimeo.com/123"))
        #expect(!Youtube.esUrlDeYoutube("esto no es una url"))
    }

    @Test("la dirección del oEmbed lleva la URL escapada")
    func direccionOEmbed() {
        let direccion = Youtube.urlOEmbed(para: "https://youtu.be/abc?t=30")?.absoluteString

        #expect(direccion?.contains("https%3A%2F%2Fyoutu.be%2Fabc%3Ft%3D30") == true)
        #expect(direccion?.contains("format=json") == true)
    }

    @Test("de la respuesta saca título y miniatura, y lo da por vídeo")
    func leeLaRespuesta() {
        let datos = Data(
            #"{"title": "Un vídeo", "thumbnail_url": "https://i.ytimg.com/a.jpg"}"#.utf8
        )

        #expect(
            Youtube.metadatos(desdeOEmbed: datos)
                == MetadatosExtraidos(
                    titulo: "Un vídeo",
                    imagenUrl: "https://i.ytimg.com/a.jpg",
                    tipo: .video
                )
        )
    }

    @Test("una respuesta que no se entiende no inventa metadatos")
    func respuestaIlegible() {
        #expect(Youtube.metadatos(desdeOEmbed: Data("no soy json".utf8)) == nil)
    }
}
