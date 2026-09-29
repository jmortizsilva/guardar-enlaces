package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Los mismos casos que `test_presentacion.py` de Windows y `PruebasTextos.swift` del iPhone, con
 * las mismas frases: si uno cambia, los otros dos tienen que cambiar con él.
 */
class PruebasTextosImportar {
    @Test
    fun `concuerda los plurales de verdad, nunca enlace(s)`() {
        assertEquals(
            "Importados 143 enlaces. 12 ya los tenías.",
            Textos.resultadoImportacion(143, 12),
        )
        assertEquals("Importados 143 enlaces.", Textos.resultadoImportacion(143, 0))
        assertEquals("Importado 1 enlace.", Textos.resultadoImportacion(1, 0))
        assertEquals("Importado 1 enlace. Uno ya lo tenías.", Textos.resultadoImportacion(1, 1))
        assertEquals("Importados 2 enlaces. Uno ya lo tenías.", Textos.resultadoImportacion(2, 1))
    }

    @Test
    fun `cuando no entra nada dice por que, y distingue las dos razones`() {
        assertEquals(
            "No hay nada nuevo: los 12 enlaces del fichero ya los tenías.",
            Textos.resultadoImportacion(0, 12),
        )
        assertEquals(
            "No hay nada nuevo: el único enlace del fichero ya lo tenías.",
            Textos.resultadoImportacion(0, 1),
        )
        assertEquals("Ese fichero no tiene ninguna dirección.", Textos.resultadoImportacion(0, 0))
    }
}
