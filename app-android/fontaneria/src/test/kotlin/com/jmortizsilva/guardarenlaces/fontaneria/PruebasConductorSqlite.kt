package com.jmortizsilva.guardarenlaces.fontaneria

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Comprueba que el SQLite empaquetado carga en la JVM de este Mac. Todo el almacén se va a probar
 * con él, y si no carga no hay fase 2 tal como está planteada (ver PLAN.md, «Cómo se habla con
 * SQLite»).
 */
class PruebasConductorSqlite {
    @Test
    fun `abre una base en memoria y responde`() {
        val conexion = BundledSQLiteDriver().open(":memory:")
        conexion.execSQL("CREATE TABLE t (id TEXT PRIMARY KEY)")
        conexion.execSQL("INSERT INTO t (id) VALUES ('a')")
        val cuantos =
            conexion.prepare("SELECT COUNT(*) FROM t").use { sentencia ->
                sentencia.step()
                sentencia.getLong(0)
            }
        conexion.close()
        assertEquals(1, cuantos)
    }
}
