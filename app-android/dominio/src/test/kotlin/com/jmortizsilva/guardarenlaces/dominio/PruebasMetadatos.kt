package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PruebasExtraerMetadatos {
    @Test
    fun `prefiere og-title al titulo de la pestana`() {
        val html =
            """
            <html><head>
            <title>El de la pestaña</title>
            <meta property="og:title" content="Café &amp; teoría">
            <meta property="og:description" content="Una descripción">
            <meta content="https://ejemplo.com/foto.jpg" property="og:image">
            <meta property="og:type" content="article">
            </head></html>
            """

        assertEquals(
            MetadatosExtraidos(
                titulo = "Café & teoría",
                descripcion = "Una descripción",
                imagenUrl = "https://ejemplo.com/foto.jpg",
                tipo = TipoElemento.Articulo,
            ),
            Metadatos.extraer(html),
        )
    }

    @Test
    fun `sin og-title cae al titulo de la pestana, sin espacios de sobra`() {
        assertEquals(
            MetadatosExtraidos(titulo = "Solo esto"),
            Metadatos.extraer("<html><head><title>  Solo esto  </title></head></html>"),
        )
    }

    @Test
    fun `una pagina sin nada no rompe, todo vacio y tipo enlace`() {
        assertEquals(MetadatosExtraidos(), Metadatos.extraer("<html></html>"))
    }

    @Test
    fun `el tipo sale de og-type, y lo que no se reconozca es un enlace`() {
        fun tipoDe(ogType: String) =
            Metadatos.extraer("<meta property=\"og:type\" content=\"$ogType\">").tipo

        assertEquals(TipoElemento.Video, tipoDe("video.other"))
        assertEquals(TipoElemento.Articulo, tipoDe("article"))
        assertEquals(TipoElemento.Imagen, tipoDe("image"))
        assertEquals(TipoElemento.Enlace, tipoDe("website"))
    }

    @Test
    fun `solo se traducen las cinco entidades que traduce el backend`() {
        // No es un descuido: el servidor resuelve los metadatos cuando hay cuenta y el teléfono
        // cuando no la hay. Traducir aquí más entidades haría que el mismo enlace se viera
        // distinto según quién lo resolvió.
        val html = "<meta property=\"og:title\" content=\"Caf&eacute; &amp; teor&iacute;a\">"

        assertEquals("Caf&eacute; & teor&iacute;a", Metadatos.extraer(html).titulo)
    }
}

class PruebasYoutube {
    @Test
    fun `reconoce sus dominios, incluidos youtu-be y el movil`() {
        assertTrue(Youtube.esUrlDeYoutube("https://www.youtube.com/watch?v=abc"))
        assertTrue(Youtube.esUrlDeYoutube("https://youtu.be/abc"))
        assertTrue(Youtube.esUrlDeYoutube("https://m.youtube.com/watch?v=abc"))
        assertFalse(Youtube.esUrlDeYoutube("https://vimeo.com/123"))
        assertFalse(Youtube.esUrlDeYoutube("esto no es una url"))
    }

    @Test
    fun `la direccion del oEmbed lleva la URL escapada`() {
        val direccion = Youtube.urlOEmbed("https://youtu.be/abc?t=30")

        assertTrue("https%3A%2F%2Fyoutu.be%2Fabc%3Ft%3D30" in direccion)
        assertTrue("format=json" in direccion)
    }

    @Test
    fun `escapa todo lo que no sea letra, numero o -_~, y no como un formulario`() {
        // Un espacio es %20 y no +, y una eñe son sus dos bytes en UTF-8.
        assertEquals(
            "https://www.youtube.com/oembed?url=a%20%C3%B1%2A-._~&format=json",
            Youtube.urlOEmbed("a ñ*-._~"),
        )
    }

    @Test
    fun `de la respuesta saca titulo y miniatura, y lo da por video`() {
        assertEquals(
            MetadatosExtraidos(
                titulo = "Un vídeo",
                imagenUrl = "https://i.ytimg.com/a.jpg",
                tipo = TipoElemento.Video,
            ),
            Youtube.metadatos(
                """{"title": "Un vídeo", "thumbnail_url": "https://i.ytimg.com/a.jpg"}"""
            ),
        )
    }

    @Test
    fun `una respuesta que no se entiende no inventa metadatos`() {
        assertNull(Youtube.metadatos("no soy json"))
    }
}
