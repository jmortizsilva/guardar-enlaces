package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PruebasTextosLista {
    @Test
    fun `el filtro dice por donde esta filtrando`() {
        assertEquals("Filtrar por etiqueta: Todas", Textos.filtroPorEtiqueta(null))
        assertEquals("Filtrar por etiqueta: ocio", Textos.filtroPorEtiqueta("ocio"))
    }

    @Test
    fun `la lista vacia distingue no tener nada de que el filtro no encuentre`() {
        assertEquals("No hay enlaces guardados todavía.", Textos.listaVacia("", null))
        assertEquals("Ningún enlace con «cookies».", Textos.listaVacia("cookies", null))
        assertEquals("Ningún enlace con la etiqueta «ocio».", Textos.listaVacia("", "ocio"))
        assertEquals(
            "Ningún enlace con «cookies» y la etiqueta «ocio».",
            Textos.listaVacia("cookies", "ocio"),
        )
    }

    @Test
    fun `una busqueda de solo espacios cuenta como no buscar nada`() {
        assertEquals("No hay enlaces guardados todavía.", Textos.listaVacia("   ", null))
    }
}

class PruebasTextosAnuncios {
    @Test
    fun `la pregunta de eliminar dice que se elimina`() {
        assertEquals(
            "¿Eliminar «Alternativas a Pocket»?",
            Textos.preguntaEliminar("Alternativas a Pocket"),
        )
    }

    @Test
    fun `con cuenta avisa de que tambien se va del ordenador`() {
        assertEquals("Se eliminará también en el ordenador.", Textos.consecuenciaEliminar(true))
        assertEquals("Está guardado solo en este teléfono.", Textos.consecuenciaEliminar(false))
    }

    @Test
    fun `al eliminar se oye primero la accion y despues el enlace`() {
        assertEquals(
            "Eliminado, Alternativas a Pocket",
            Textos.eliminado("Alternativas a Pocket"),
        )
    }

    @Test
    fun `las etiquetas se anuncian por como quedan, no por que se hayan guardado`() {
        assertEquals(
            "Etiquetas: ocio, pendiente",
            Textos.etiquetasGuardadas(listOf("ocio", "pendiente")),
        )
        assertEquals("Sin etiquetas", Textos.etiquetasGuardadas(emptyList()))
    }

    @Test
    fun `el plural de la sincronizacion esta concordado, sin parentesis`() {
        assertEquals("Sincronizado, sin cambios", Textos.sincronizacionTerminada(0))
        assertEquals("Sincronizado, 1 enlace nuevo", Textos.sincronizacionTerminada(1))
        assertEquals("Sincronizado, 3 enlaces nuevos", Textos.sincronizacionTerminada(3))
    }

    @Test
    fun `el fallo lleva la accion delante y la causa detras`() {
        assertEquals(
            "No se pudo sincronizar: sin conexión con el servidor",
            Textos.sincronizacionFallida("sin conexión con el servidor"),
        )
    }
}

class PruebasTextosAnadir {
    @Test
    fun `el boton de etiquetas dice cuales llevas`() {
        assertEquals("Etiquetas: ninguna", Textos.botonEtiquetas(emptyList()))
        assertEquals(
            "Etiquetas: ocio, pendiente",
            Textos.botonEtiquetas(listOf("ocio", "pendiente")),
        )
    }

    @Test
    fun `guardar y actualizar se anuncian distinto, que no es lo mismo`() {
        assertEquals("Guardado, Un artículo", Textos.guardado("Un artículo"))
        assertEquals("Actualizado, Un artículo", Textos.actualizado("Un artículo"))
    }

    @Test
    fun `si la comprobacion fallo, el anuncio dice que falto`() {
        assertEquals(
            "Guardado sin título, no se pudo comprobar la página",
            Textos.guardadoSinComprobar,
        )
    }

    @Test
    fun `el aviso del portapapeles dice que hay y que se puede hacer`() {
        assertEquals("Hay un enlace copiado, puedes pegarlo", Textos.hayEnlaceCopiado)
        assertEquals("Hay texto copiado, puedes pegarlo", Textos.hayTextoCopiado)
    }
}

class PruebasTextosAjustes {
    @Test
    fun `el singular y el plural de los enlaces que ya habia`() {
        assertTrue(
            Textos.preguntaImportar(1, deOtraCuenta = false)
                .startsWith("Hay 1 enlace guardado en este teléfono sin cuenta.")
        )
        assertTrue(
            Textos.preguntaImportar(3, deOtraCuenta = true)
                .startsWith("Hay 3 enlaces guardados en este teléfono con otra cuenta.")
        )
    }

    @Test
    fun `la pregunta dice que borrar no se puede deshacer`() {
        assertTrue("no se pueden recuperar" in Textos.preguntaImportar(2, deOtraCuenta = false))
    }

    @Test
    fun `con sesion iniciada se dice con que cuenta`() {
        assertEquals(
            "Sesión iniciada como persona@ejemplo.com. Tus enlaces se sincronizan con el ordenador.",
            Textos.sesionIniciadaComo("persona@ejemplo.com"),
        )
    }
}

class PruebasTextosSinIphone {
    @Test
    fun `ningun texto habla del iPhone ni del modo lector`() {
        // Los textos salen de los de iOS. Esto caza el que se haya quedado sin adaptar.
        val todos =
            Textos::class
                .java
                .declaredFields
                .filter { it.type == String::class.java }
                .map {
                    it.isAccessible = true
                    it.get(null) as String
                } +
                listOf(
                    Textos.consecuenciaEliminar(false),
                    Textos.preguntaImportar(2, deOtraCuenta = false),
                )

        assertTrue(todos.size > 50)
        for (texto in todos) {
            assertFalse("iPhone" in texto, texto)
            assertFalse("modo lector" in texto, texto)
            assertFalse("Safari" in texto, texto)
        }
    }
}

class PruebasRecorte {
    @Test
    fun `lo corto se queda igual`() {
        assertEquals("Alternativas a Pocket", Textos.recortado("Alternativas a Pocket"))
    }

    @Test
    fun `lo largo se corta por la ultima palabra que cabe`() {
        val largo =
            "Las mejores alternativas a Pocket para guardar artículos y leerlos más tarde en " +
                "cualquier dispositivo"

        val corto = Textos.recortado(largo)

        assertTrue(corto.length <= 61)
        assertTrue(corto.endsWith("…"))
        // Cortar a mitad de palabra suena a error de la aplicación.
        assertFalse("artícu…" in corto)
    }

    @Test
    fun `una palabra sola larguisima se corta igual, aunque quede partida`() {
        val corto = Textos.recortado("a".repeat(100))

        assertEquals(61, corto.length)
        assertTrue(corto.endsWith("…"))
    }

    @Test
    fun `no parte un emoji por la mitad`() {
        // Un emoji son dos unidades de `length`. Cortando por ahí queda medio carácter, que el
        // lector de pantalla lee como un símbolo raro.
        val corto = Textos.recortado("😀".repeat(100))

        assertEquals("😀".repeat(60) + "…", corto)
    }
}
