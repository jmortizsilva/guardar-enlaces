package com.jmortizsilva.guardarenlaces.dominio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Como las de importar, estas pruebas no traen sus casos: leen
 * `pruebas-compartidas/direcciones/casos.json`, el mismo que leen Windows y el iPhone.
 */
class PruebasDirecciones {
    private val casos: File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "pruebas-compartidas/direcciones/casos.json") }
            .firstOrNull { it.isFile }
            ?: error("no se encuentra pruebas-compartidas/direcciones subiendo desde user.dir")

    @Test
    fun `completa como dicen los casos compartidos`() {
        val lista = Json.parseToJsonElement(casos.readText()).jsonObject.getValue("casos").jsonArray
        assertTrue(lista.size > 10)
        for (caso in lista.map { it.jsonObject }) {
            val escrito = caso.getValue("escrito").jsonPrimitive.content
            val direccion = caso.getValue("direccion").jsonPrimitive.contentOrNull
            val esperado = direccion?.let {
                DireccionEscrita(it, caso.getValue("alternativa").jsonPrimitive.contentOrNull)
            }
            assertEquals(esperado, Enlaces.completar(escrito), "con «$escrito»")
        }
    }
}
