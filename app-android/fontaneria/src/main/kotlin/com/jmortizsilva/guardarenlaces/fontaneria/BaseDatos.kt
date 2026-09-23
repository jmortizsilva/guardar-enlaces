package com.jmortizsilva.guardarenlaces.fontaneria

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement

/**
 * Lo justo de SQLite para lo que hace esta app: abrir, ejecutar, consultar y agrupar en una
 * transacción.
 *
 * El conductor se recibe de fuera: en el teléfono es el SQLite del sistema, y en las pruebas el
 * empaquetado, que funciona en la JVM del Mac. El SQL es el mismo en los dos.
 *
 * Una conexión de SQLite no se puede usar desde dos hilos a la vez, y aquí la usan la pantalla y la
 * sincronización. Todo pasa por un cerrojo; las consultas tardan microsegundos, así que nadie
 * espera de verdad.
 */
internal class BaseDatos(conductor: SQLiteDriver, ruta: String) {
    private val conexion: SQLiteConnection = conductor.open(ruta)
    private val cerrojo = Any()
    private var profundidad = 0

    fun cerrar() = synchronized(cerrojo) { conexion.close() }

    fun ejecutar(sql: String, vararg parametros: Any?) {
        synchronized(cerrojo) {
            conexion.prepare(sql).use { sentencia ->
                enlazar(sentencia, parametros)
                // Un solo paso, aunque la sentencia devuelva una fila, como `PRAGMA journal_mode`.
                // Más de uno rompe en el teléfono: el conductor del sistema trata aparte ese
                // PRAGMA (para activar el WAL por la vía de Android) y después de cada paso lee la
                // columna 0; en el segundo ya no hay fila y lanza «Index 1 requested, with a size
                // of 1». El conductor de las pruebas del Mac no hace eso, así que allí no salía.
                // Visto en androidx.sqlite 2.7.1, `JournalModeSetStatement`.
                sentencia.step()
            }
        }
    }

    fun <T> consultar(sql: String, vararg parametros: Any?, leer: (Fila) -> T): List<T> =
        synchronized(cerrojo) {
            conexion.prepare(sql).use { sentencia ->
                enlazar(sentencia, parametros)
                buildList { while (sentencia.step()) add(leer(Fila(sentencia))) }
            }
        }

    /**
     * Todo o nada, y de una vez. Sin esto, cada sentencia es su propia transacción: una bajada de
     * mil enlaces serían mil escrituras a disco en el teléfono, y si la app se cierra a la mitad
     * queda media bajada guardada con el cursor sin mover.
     *
     * Admite anidarse: la de dentro se suma a la de fuera.
     */
    fun <T> enTransaccion(bloque: () -> T): T =
        synchronized(cerrojo) {
            if (profundidad > 0) return bloque()
            ejecutar("BEGIN IMMEDIATE")
            profundidad++
            try {
                bloque().also {
                    profundidad--
                    ejecutar("COMMIT")
                }
            } catch (fallo: Throwable) {
                profundidad--
                ejecutar("ROLLBACK")
                throw fallo
            }
        }

    private fun enlazar(sentencia: SQLiteStatement, parametros: Array<out Any?>) {
        parametros.forEachIndexed { posicion, valor ->
            val indice = posicion + 1
            when (valor) {
                null -> sentencia.bindNull(indice)
                is String -> sentencia.bindText(indice, valor)
                is Long -> sentencia.bindLong(indice, valor)
                is Int -> sentencia.bindLong(indice, valor.toLong())
                is Boolean -> sentencia.bindLong(indice, if (valor) 1 else 0)
                else -> error("no se sabe guardar un ${valor::class.simpleName} en SQLite")
            }
        }
    }

    class Fila(private val sentencia: SQLiteStatement) {
        fun texto(columna: Int): String? =
            if (sentencia.isNull(columna)) null else sentencia.getText(columna)

        fun textoObligatorio(columna: Int): String = sentencia.getText(columna)

        fun entero(columna: Int): Long = sentencia.getLong(columna)

        fun bandera(columna: Int): Boolean = sentencia.getLong(columna) != 0L
    }
}
