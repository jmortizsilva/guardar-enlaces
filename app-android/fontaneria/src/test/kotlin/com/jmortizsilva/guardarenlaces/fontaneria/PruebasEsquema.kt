package com.jmortizsilva.guardarenlaces.fontaneria

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El esquema, columna por columna, contra el de Windows (`app-windows/guardar_enlaces/
 * almacen_local.py`, copiado aquí el 2026-09-23). Son clientes del mismo contrato y cuando algo
 * falla en uno hay que poder abrir la base del otro y entenderla.
 *
 * Si esta prueba falla es que alguien ha cambiado el esquema aquí; el cambio se decide para los
 * tres clientes, no para uno.
 */
class PruebasEsquema {
    /** Nombre, tipo, obligatoria, valor por defecto y si es clave, como lo da `table_info`. */
    private val esquemaDeWindows =
        mapOf(
            "elementos" to
                listOf(
                    "id TEXT 0 null 1",
                    "url TEXT 1 null 0",
                    "titulo TEXT 0 null 0",
                    "descripcion TEXT 0 null 0",
                    "imagen_url TEXT 0 null 0",
                    "tipo TEXT 1 'enlace' 0",
                    "etiquetas TEXT 1 '[]' 0",
                    "creado_en INTEGER 1 null 0",
                    "actualizado_en INTEGER 1 null 0",
                    "borrado INTEGER 1 0 0",
                ),
            "outbox" to listOf("id TEXT 0 null 1"),
            "etiquetas_definidas" to
                listOf(
                    "id TEXT 0 null 1",
                    "nombre TEXT 1 null 0",
                    "creado_en INTEGER 1 null 0",
                    "actualizado_en INTEGER 1 null 0",
                    "borrado INTEGER 1 0 0",
                ),
            "outbox_etiquetas" to listOf("id TEXT 0 null 1"),
            "estado_sincronizacion" to listOf("clave TEXT 0 null 1", "valor INTEGER 1 null 0"),
            "estado_texto" to listOf("clave TEXT 0 null 1", "valor TEXT 1 null 0"),
        )

    @Test
    fun `las tablas y sus columnas son las de Windows`() {
        val fichero = Files.createTempFile("guardalo", ".db")
        AlmacenLocal(BundledSQLiteDriver(), fichero.toString()).cerrar()

        val conexion = BundledSQLiteDriver().open(fichero.toString())
        val tablas =
            conexion
                .prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
                .use { buildList { while (it.step()) add(it.getText(0)) } }
        val columnas = tablas.associateWith { tabla ->
            conexion.prepare("PRAGMA table_info($tabla)").use {
                buildList {
                    while (it.step()) {
                        val porDefecto = if (it.isNull(4)) "null" else it.getText(4)
                        add(
                            "${it.getText(1)} ${it.getText(2)} ${it.getLong(3)} $porDefecto ${it.getLong(5)}"
                        )
                    }
                }
            }
        }
        conexion.close()
        borrarBase(fichero)

        assertEquals(esquemaDeWindows.keys.sorted(), tablas)
        assertEquals(esquemaDeWindows, columnas)
    }

    @Test
    fun `una base creada por otro cliente con las columnas en otro orden se lee bien`() {
        // Se leen por nombre y no con `SELECT *`: una base que venga de otra versión o de otro
        // cliente con las columnas en otro orden no puede cruzar el título con la descripción.
        val fichero = Files.createTempFile("guardalo", ".db")
        BundledSQLiteDriver().open(fichero.toString()).apply {
            execSQL(
                """
                CREATE TABLE elementos (
                    borrado INTEGER NOT NULL DEFAULT 0, descripcion TEXT, titulo TEXT,
                    id TEXT PRIMARY KEY, url TEXT NOT NULL, imagen_url TEXT,
                    tipo TEXT NOT NULL DEFAULT 'enlace', etiquetas TEXT NOT NULL DEFAULT '[]',
                    creado_en INTEGER NOT NULL, actualizado_en INTEGER NOT NULL
                )
                """
            )
            execSQL(
                "INSERT INTO elementos (id, url, titulo, descripcion, creado_en, actualizado_en) " +
                    "VALUES ('e1', 'https://a.com', 'El título', 'La descripción', 1, 2)"
            )
            close()
        }

        val almacen = AlmacenLocal(BundledSQLiteDriver(), fichero.toString())
        val leido = almacen.cargarTodos()["e1"]
        almacen.cerrar()
        borrarBase(fichero)

        assertEquals(
            Elemento(
                id = "e1",
                url = "https://a.com",
                titulo = "El título",
                descripcion = "La descripción",
                creadoEn = 1,
                actualizadoEn = 2,
            ),
            leido,
        )
    }
}

/** Con `WAL`, SQLite deja dos ficheros más junto a la base: se van todos. */
private fun borrarBase(fichero: java.nio.file.Path) {
    for (sufijo in listOf("", "-wal", "-shm")) {
        Files.deleteIfExists(fichero.resolveSibling(fichero.fileName.toString() + sufijo))
    }
}
