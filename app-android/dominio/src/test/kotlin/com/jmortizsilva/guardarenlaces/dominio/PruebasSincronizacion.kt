package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun nuevo(
    url: String,
    titulo: String? = null,
    en: MarcaDeTiempo = 100,
    generarId: GeneradorId,
) =
    nuevoElementoLocal(
        DatosElementoNuevo(url = url, titulo = titulo),
        ahora = relojFijo(en),
        generarId = generarId,
    )

class PruebasAplicarPull {
    @Test
    fun `lo que no estaba se anade`() {
        val recibido = nuevo("https://a.com", generarId = generadorSecuencial())

        val cache = Sincronizacion.aplicarPull(emptyMap(), listOf(recibido), emptyMap())

        assertEquals(recibido, cache[recibido.id])
    }

    @Test
    fun `no pisa un cambio local que todavia esta sin subir y es mas nuevo`() {
        val base = nuevo("https://a.com", titulo = "Original", generarId = generadorSecuencial())
        val pendiente = base.conEtiquetas(listOf("editado aquí"), ahora = relojFijo(300))
        val delServidor = base.conEtiquetas(listOf("vieja del servidor"), ahora = relojFijo(200))

        val cache =
            Sincronizacion.aplicarPull(
                mapOf(base.id to base),
                listOf(delServidor),
                mapOf(base.id to pendiente),
            )

        assertEquals(base, cache[base.id])
    }

    @Test
    fun `si lo que baja es mas nuevo que lo pendiente, si se aplica`() {
        val base = nuevo("https://a.com", titulo = "Original", generarId = generadorSecuencial())
        val pendiente = base.conEtiquetas(listOf("editado aquí"), ahora = relojFijo(150))
        val delServidor = base.conEtiquetas(listOf("de otro sitio"), ahora = relojFijo(500))

        val cache =
            Sincronizacion.aplicarPull(
                mapOf(base.id to base),
                listOf(delServidor),
                mapOf(base.id to pendiente),
            )

        assertEquals(delServidor, cache[base.id])
    }
}

class PruebasAplicarPush {
    @Test
    fun `manda la version del servidor, aunque sea mas antigua`() {
        val local = nuevo("https://a.com", titulo = "Mío", generarId = generadorSecuencial())
        val definitivo = local.conEtiquetas(listOf("el servidor ganó"), ahora = relojFijo(50))

        val cache =
            Sincronizacion.aplicarRespuestaPush(mapOf(local.id to local), listOf(definitivo))

        assertEquals(definitivo, cache[local.id])
    }
}

class PruebasElementosVisibles {
    @Test
    fun `oculta los borrados y pone lo guardado mas recientemente primero`() {
        val generar = generadorSecuencial()
        val a = nuevo("https://a.com", en = 100, generarId = generar)
        val b = nuevo("https://b.com", en = 200, generarId = generar)
        val c =
            nuevo("https://c.com", en = 300, generarId = generar)
                .marcadoComoBorrado(ahora = relojFijo(400))

        val visibles = Sincronizacion.elementosVisibles(mapOf(a.id to a, b.id to b, c.id to c))

        assertEquals(listOf(b.id, a.id), visibles.map { it.id })
    }

    @Test
    fun `retocar un enlace viejo no lo manda al principio de la lista`() {
        val generar = generadorSecuencial()
        val viejo = nuevo("https://viejo.com", en = 100, generarId = generar)
        val nuevoEnlace = nuevo("https://nuevo.com", en = 200, generarId = generar)
        val viejoRetocado = viejo.conEtiquetas(listOf("ocio"), ahora = relojFijo(900))

        val visibles =
            Sincronizacion.elementosVisibles(
                mapOf(viejoRetocado.id to viejoRetocado, nuevoEnlace.id to nuevoEnlace)
            )

        assertEquals(listOf(nuevoEnlace.id, viejoRetocado.id), visibles.map { it.id })
    }

    @Test
    fun `dos guardados en el mismo instante salen por identificador`() {
        // Importar una biblioteca entera pone la misma fecha a todo. Sin desempate, la lista se
        // recolocaría sola entre dos aperturas.
        val b = Elemento(id = "b", url = "https://b.com", creadoEn = 100)
        val a = Elemento(id = "a", url = "https://a.com", creadoEn = 100)

        assertEquals(
            listOf("a", "b"),
            Sincronizacion.elementosVisibles(mapOf("b" to b, "a" to a)).map { it.id },
        )
    }
}

class PruebasSincronizacionEtiquetas {
    @Test
    fun `no pisa una etiqueta local sin subir que es mas nueva`() {
        val base =
            nuevaEtiquetaDefinida("ocio", ahora = relojFijo(100), generarId = generadorSecuencial())
        val pendiente = base.renombrada("tiempo libre", ahora = relojFijo(300))
        val delServidor = base.renombrada("ocio", ahora = relojFijo(200))

        val cache =
            Sincronizacion.aplicarPullEtiquetas(
                mapOf(base.id to base),
                listOf(delServidor),
                mapOf(base.id to pendiente),
            )

        assertEquals(base, cache[base.id])
    }

    @Test
    fun `las borradas no se ofrecen, y las demas salen por nombre`() {
        val generar = generadorSecuencial()
        val ocio = nuevaEtiquetaDefinida("ocio", generarId = generar)
        val casa = nuevaEtiquetaDefinida("casa", generarId = generar)
        val ida = nuevaEtiquetaDefinida("ida", generarId = generar).marcadaComoBorrada()

        val visibles =
            Sincronizacion.etiquetasReservadasVisibles(
                mapOf(ocio.id to ocio, casa.id to casa, ida.id to ida)
            )

        assertEquals(listOf("casa", "ocio"), visibles.map { it.nombre })
    }

    @Test
    fun `la respuesta del servidor manda tambien en las etiquetas`() {
        val local =
            nuevaEtiquetaDefinida("ocio", ahora = relojFijo(300), generarId = generadorSecuencial())
        val definitiva = local.renombrada("ganó el servidor", ahora = relojFijo(100))

        val cache =
            Sincronizacion.aplicarRespuestaPushEtiquetas(
                mapOf(local.id to local),
                listOf(definitiva),
            )

        assertEquals(definitiva, cache[local.id])
    }
}

class PruebasTocaSincronizar {
    @Test
    fun `sin ninguna sincronizacion previa, toca`() {
        // El cero no es «hace un instante»: es 1970.
        assertTrue(Sincronizacion.tocaSincronizar(ultima = 0, ahora = relojDelSistema()))
    }

    @Test
    fun `volver a la app dos veces seguidas no lanza dos sincronizaciones`() {
        assertFalse(Sincronizacion.tocaSincronizar(ultima = 1000, ahora = 2000, intervalo = 30_000))
    }

    @Test
    fun `pasado el intervalo vuelve a tocar`() {
        assertTrue(
            Sincronizacion.tocaSincronizar(ultima = 1000, ahora = 31_000, intervalo = 30_000)
        )
    }
}
