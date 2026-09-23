package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException

class PruebasElementoNuevo {
    @Test
    fun `toma el identificador y las fechas de lo que se le inyecta`() {
        val elemento =
            nuevoElementoLocal(
                DatosElementoNuevo(url = "https://a.com", titulo = "A"),
                ahora = relojFijo(1000),
                generarId = generadorSecuencial(),
            )

        assertEquals("https://a.com", elemento.url)
        assertEquals("A", elemento.titulo)
        assertEquals(1000, elemento.creadoEn)
        assertEquals(1000, elemento.actualizadoEn)
        assertFalse(elemento.borrado)
        assertEquals("id-de-prueba-1", elemento.id)
    }

    @Test
    fun `dos elementos no comparten identificador`() {
        val generar = generadorSecuencial()
        val a = nuevoElementoLocal(DatosElementoNuevo("https://a.com"), generarId = generar)
        val b = nuevoElementoLocal(DatosElementoNuevo("https://b.com"), generarId = generar)

        assertNotEquals(a.id, b.id)
    }
}

class PruebasCambiosElemento {
    private fun elementoDePrueba(instante: MarcaDeTiempo = 100) =
        nuevoElementoLocal(
            DatosElementoNuevo(url = "https://a.com", titulo = "Viejo"),
            ahora = relojFijo(instante),
            generarId = generadorSecuencial(),
        )

    @Test
    fun `borrar deja una lapida, no tira los datos`() {
        val borrado = elementoDePrueba().marcadoComoBorrado(ahora = relojFijo(200))

        assertTrue(borrado.borrado)
        assertEquals(200, borrado.actualizadoEn)
        assertEquals("https://a.com", borrado.url)
        assertEquals("Viejo", borrado.titulo)
    }

    @Test
    fun `cambiar las etiquetas mueve la fecha y respeta el identificador`() {
        val original = elementoDePrueba()
        val editado = original.conEtiquetas(listOf("ocio"), ahora = relojFijo(200))

        assertEquals(listOf("ocio"), editado.etiquetas)
        assertEquals(200, editado.actualizadoEn)
        assertEquals(original.id, editado.id)
        assertEquals(original.creadoEn, editado.creadoEn)
    }

    @Test
    fun `los metadatos se pueden completar despues de guardar`() {
        val editado =
            elementoDePrueba()
                .conMetadatos(
                    titulo = "Nuevo",
                    descripcion = "Una descripción",
                    imagenUrl = "https://a.com/i.jpg",
                    tipo = TipoElemento.Articulo,
                    ahora = relojFijo(200),
                )

        assertEquals("Nuevo", editado.titulo)
        assertEquals(TipoElemento.Articulo, editado.tipo)
        assertEquals(200, editado.actualizadoEn)
    }
}

class PruebasElementoJson {
    @Test
    fun `ir al JSON y volver no pierde nada`() {
        val original =
            nuevoElementoLocal(
                DatosElementoNuevo(
                    url = "https://a.com",
                    titulo = "A",
                    descripcion = "d",
                    imagenUrl = "https://a.com/i.jpg",
                    tipo = TipoElemento.Video,
                    etiquetas = listOf("ocio", "pendiente"),
                ),
                ahora = relojFijo(100),
                generarId = generadorSecuencial(),
            )

        assertEquals(original, idaYVuelta(original))
    }

    @Test
    fun `una baja que solo trae identificador y fecha se entiende igual`() {
        val elemento = desdeJson<Elemento>("""{"id": "x1", "actualizadoEn": 5, "borrado": true}""")

        assertEquals("", elemento.url)
        assertNull(elemento.titulo)
        assertEquals(emptyList(), elemento.etiquetas)
        assertEquals(TipoElemento.Enlace, elemento.tipo)
        assertEquals(0, elemento.creadoEn)
        assertTrue(elemento.borrado)
    }

    @Test
    fun `un tipo que esta version no conoce no tira el enlace`() {
        val elemento =
            desdeJson<Elemento>("""{"id": "x1", "url": "https://a.com", "tipo": "pódcast"}""")

        assertEquals(TipoElemento.Enlace, elemento.tipo)
        assertEquals("https://a.com", elemento.url)
    }

    @Test
    fun `un null o un campo nuevo del servidor tampoco`() {
        val elemento =
            desdeJson<Elemento>(
                """{"id": "x1", "url": "https://a.com", "tipo": null, "etiquetas": null,
                   "algoQueAunNoExiste": 3}"""
            )

        assertEquals(TipoElemento.Enlace, elemento.tipo)
        assertEquals(emptyList(), elemento.etiquetas)
    }

    @Test
    fun `sin identificador no hay elemento que valga`() {
        assertFailsWith<SerializationException> {
            desdeJson<Elemento>("""{"url": "https://a.com"}""")
        }
    }

    @Test
    fun `al subirlo lleva todos los campos, tambien los que valen lo de siempre`() {
        // Para el servidor, `borrado: false` y `etiquetas: []` son datos. Si no se mandaran, una
        // edición que quita la última etiqueta no quitaría nada.
        val json = jsonDelContrato.encodeToString(Elemento(id = "x1", url = "https://a.com"))

        assertTrue("\"borrado\":false" in json)
        assertTrue("\"etiquetas\":[]" in json)
        assertTrue("\"tipo\":\"enlace\"" in json)
    }

    @Test
    fun `lo que no se sabe no se manda, igual que desde el iPhone`() {
        // Hoy el servidor trata igual un null que un campo ausente. Si algún día un null borrara,
        // un enlace guardado sin red se llevaría el título que le puso otro dispositivo.
        val json = jsonDelContrato.encodeToString(Elemento(id = "x1", url = "https://a.com"))

        assertFalse("titulo" in json)
        assertFalse("null" in json)
    }

    @Test
    fun `el tipo de la base de datos se lee igual de tolerante`() {
        assertEquals(TipoElemento.Articulo, TipoElemento.desde("articulo"))
        assertEquals(TipoElemento.Enlace, TipoElemento.desde("pódcast"))
        assertEquals(TipoElemento.Enlace, TipoElemento.desde(null))
    }
}

class PruebasEtiquetaDefinida {
    @Test
    fun `nace con nombre, identificador y las dos fechas iguales`() {
        val etiqueta =
            nuevaEtiquetaDefinida("ocio", ahora = relojFijo(100), generarId = generadorSecuencial())

        assertEquals("ocio", etiqueta.nombre)
        assertEquals("id-de-prueba-1", etiqueta.id)
        assertEquals(100, etiqueta.creadoEn)
        assertEquals(100, etiqueta.actualizadoEn)
        assertFalse(etiqueta.borrado)
    }

    @Test
    fun `renombrar y borrar mueven la fecha, no el identificador`() {
        val original =
            nuevaEtiquetaDefinida("ocio", ahora = relojFijo(100), generarId = generadorSecuencial())

        val renombrada = original.renombrada("tiempo libre", ahora = relojFijo(200))
        assertEquals("tiempo libre", renombrada.nombre)
        assertEquals(200, renombrada.actualizadoEn)
        assertEquals(original.id, renombrada.id)

        val borrada = renombrada.marcadaComoBorrada(ahora = relojFijo(300))
        assertTrue(borrada.borrado)
        assertEquals("tiempo libre", borrada.nombre)
        assertEquals(300, borrada.actualizadoEn)
    }

    @Test
    fun `ir al JSON y volver no pierde nada`() {
        val original =
            nuevaEtiquetaDefinida("ocio", ahora = relojFijo(100), generarId = generadorSecuencial())

        assertEquals(original, idaYVuelta(original))
    }

    @Test
    fun `una baja que solo trae identificador y fecha se entiende igual`() {
        val etiqueta =
            desdeJson<EtiquetaDefinida>("""{"id": "t1", "actualizadoEn": 5, "borrado": true}""")

        assertEquals("", etiqueta.nombre)
        assertTrue(etiqueta.borrado)
    }
}
