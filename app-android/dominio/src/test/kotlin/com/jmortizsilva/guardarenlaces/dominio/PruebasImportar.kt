package com.jmortizsilva.guardarenlaces.dominio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Estas pruebas NO traen sus propios casos: leen los mismos ficheros y el mismo `esperado.json` que
 * las de Windows y el iPhone, en `pruebas-compartidas/importacion/`. Ese es el único sitio donde se
 * decide qué debe salir de cada fichero.
 */
class PruebasImportar {
    /**
     * Se busca subiendo desde donde corran las pruebas, y no con una ruta fija: Gradle las lanza
     * desde la carpeta del módulo, pero un IDE puede hacerlo desde otra.
     */
    private val compartidas: File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "pruebas-compartidas/importacion") }
            .firstOrNull { it.isDirectory }
            ?: error("no se encuentra pruebas-compartidas/importacion subiendo desde user.dir")

    private val ficheros =
        listOf(
            "marcadores-navegador.html",
            "pocket.csv",
            "raindrop.csv",
            "excel.csv",
            "enlaces.txt",
        )

    private fun contenido(nombre: String) = File(compartidas, nombre).readText()

    private fun esperado(nombre: String): JsonObject =
        Json.parseToJsonElement(contenido("esperado.json")).jsonObject.getValue(nombre).jsonObject

    @Test
    fun `los ficheros compartidos estan donde se espera`() {
        for (nombre in ficheros + "esperado.json") {
            assertTrue(File(compartidas, nombre).isFile, "falta $nombre")
        }
    }

    @Test
    fun `sale exactamente lo que dice el contrato`() {
        for (nombre in ficheros) {
            val quiero = esperado(nombre)
            val elementos = quiero.getValue("elementos").jsonArray.map { it.jsonObject }
            val lectura = Importar.leer(contenido(nombre))

            assertEquals(elementos.map { it.texto("url") }, lectura.enlaces.map { it.url }, nombre)
            assertEquals(
                elementos.map { it.texto("titulo") },
                lectura.enlaces.map { it.titulo },
                nombre,
            )
            assertEquals(
                elementos.map { e ->
                    (e.getValue("etiquetas") as JsonArray).map { it.jsonPrimitive.content }
                },
                lectura.enlaces.map { it.etiquetas },
                nombre,
            )
            assertEquals(
                elementos.map { e ->
                    e.getValue("creadoEn").let {
                        if (it is JsonNull) null else it.jsonPrimitive.long
                    }
                },
                lectura.enlaces.map { it.creadoEn },
                nombre,
            )
            assertEquals(
                quiero.getValue("descartados").jsonPrimitive.int,
                lectura.descartados,
                nombre,
            )
        }
    }

    private fun JsonObject.texto(clave: String): String? =
        getValue(clave).let { if (it is JsonNull) null else it.jsonPrimitive.content }

    @Test
    fun `el formato se reconoce por el contenido, no por el nombre`() {
        assertEquals(
            Importar.Formato.Html,
            Importar.detectarFormato(contenido("marcadores-navegador.html")),
        )
        assertEquals(Importar.Formato.Csv, Importar.detectarFormato(contenido("pocket.csv")))
        assertEquals(Importar.Formato.Csv, Importar.detectarFormato(contenido("raindrop.csv")))
        assertEquals(Importar.Formato.Csv, Importar.detectarFormato(contenido("excel.csv")))
        assertEquals(Importar.Formato.Txt, Importar.detectarFormato(contenido("enlaces.txt")))
    }

    @Test
    fun `un csv sin columna de direccion no importa nada`() {
        assertTrue(Importar.leer("nombre,comentario\nAlgo,Otra cosa\n").enlaces.isEmpty())
    }

    @Test
    fun `un fichero vacio no revienta`() {
        val lectura = Importar.leer("")
        assertTrue(lectura.enlaces.isEmpty())
        assertEquals(0, lectura.descartados)
    }

    @Test
    fun `dos direcciones que solo cambian en el seguimiento cuentan como una`() {
        val lectura =
            Importar.leer("https://ejemplo.com/a?utm_source=twitter\nhttps://ejemplo.com/a\n")
        assertEquals(1, lectura.enlaces.size)
        assertEquals(1, lectura.descartados)
    }

    // --- Codificaciones ---

    private val csv = "title,url\nCanción española,https://ejemplo.com/a\n"

    @Test
    fun `el titulo llega bien en utf-8, en cp1252 y con la marca de excel`() {
        val codificaciones =
            listOf(
                csv.toByteArray(Charsets.UTF_8),
                csv.toByteArray(charset("windows-1252")),
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    csv.toByteArray(Charsets.UTF_8),
            )
        for (bytes in codificaciones) {
            val lectura = Importar.leer(Importar.texto(bytes))
            assertEquals(Importar.Formato.Csv, lectura.formato)
            assertEquals("Canción española", lectura.enlaces.firstOrNull()?.titulo)
        }
    }

    // --- Qué se guarda ---

    private fun lecturaHtml() = Importar.leer(contenido("marcadores-navegador.html"))

    @Test
    fun `sin nada guardado entran todos`() {
        val preparado = Importar.preparar(lecturaHtml(), emptyList(), ahora = { 9_000_000 })
        assertEquals(6, preparado.importados)
        assertEquals(0, preparado.yaEstaban)
    }

    @Test
    fun `un enlace que ya tienes no se toca`() {
        // Lo contrario de guardar a mano, y a propósito: importar toca cientos de golpe, así que
        // pisar lo que pusiste tú no tendría vuelta.
        val mio =
            Elemento(
                id = "mio",
                url = "https://ejemplo.com/flan",
                titulo = "Mi flan de siempre",
                etiquetas = listOf("pendiente"),
                creadoEn = 1,
                actualizadoEn = 1,
            )
        val preparado = Importar.preparar(lecturaHtml(), listOf(mio), ahora = { 9_000_000 })
        assertEquals(1, preparado.yaEstaban)
        assertFalse(preparado.nuevos.any { it.url == mio.url })
    }

    @Test
    fun `un enlace que borraste vuelve a entrar`() {
        val borrado =
            Elemento(id = "b", url = "https://ejemplo.com/flan", creadoEn = 1, actualizadoEn = 1)
                .marcadoComoBorrado(ahora = { 2 })
        val preparado = Importar.preparar(lecturaHtml(), listOf(borrado), ahora = { 9_000_000 })
        assertEquals(0, preparado.yaEstaban)
    }

    @Test
    fun `la fecha del fichero manda sobre la del reloj`() {
        val preparado = Importar.preparar(lecturaHtml(), emptyList(), ahora = { 9_000_000 })
        val porTitulo = preparado.nuevos.associate { it.titulo to it.creadoEn }
        assertEquals(1_700_000_300_000, porTitulo["Arroz caldoso"])
        assertEquals(9_000_000, porTitulo["Un marcador sin ADD_DATE"])
    }
}
