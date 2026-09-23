package com.jmortizsilva.guardarenlaces.fontaneria

import androidx.sqlite.SQLiteDriver
import com.jmortizsilva.guardarenlaces.dominio.Cache
import com.jmortizsilva.guardarenlaces.dominio.CacheEtiquetas
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.EtiquetaDefinida
import com.jmortizsilva.guardarenlaces.dominio.GeneradorId
import com.jmortizsilva.guardarenlaces.dominio.MarcaDeTiempo
import com.jmortizsilva.guardarenlaces.dominio.TipoElemento
import com.jmortizsilva.guardarenlaces.dominio.generarIdUnico
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Caché local (SQLite) más la cola de cambios pendientes de subir.
 *
 * Aquí solo se persiste; cómo fusionar lo decide `dominio`, que se prueba sin base de datos. La
 * cola es sencilla a propósito: solo el conjunto de identificadores con un cambio local sin
 * confirmar. El elemento en sí ya está en su tabla, actualizado en el momento del cambio, para que
 * la pantalla lo vea al instante sin esperar a la red.
 *
 * Mismo esquema que Windows e iOS, tabla por tabla y columna por columna: son clientes del mismo
 * contrato, y cuando algo falle en uno hay que poder mirar el otro.
 *
 * @param ruta un fichero, o `":memory:"` para una base que vive solo mientras dure el objeto (lo
 *   que usan las pruebas).
 */
class AlmacenLocal(conductor: SQLiteDriver, ruta: String) {
    private val bd = BaseDatos(conductor, ruta)

    init {
        // La sincronización escribe mientras la pantalla lee. Con el diario de siempre, quien
        // escribe bloquea la base entera; `WAL` deja leer mientras tanto, y la espera convierte el
        // choque que quede en unos milisegundos de cola en vez de un fallo. En Android no hay dos
        // procesos, como en iOS con la extensión, pero sí dos hilos.
        bd.ejecutar("PRAGMA journal_mode = WAL")
        bd.ejecutar("PRAGMA busy_timeout = 5000")
        esquema.forEach { bd.ejecutar(it) }
    }

    fun cerrar() = bd.cerrar()

    // Cuenta a la que pertenece lo guardado

    /**
     * Borra la caché entera: enlaces, etiquetas, colas, cursor y dueño. Se llama al cerrar sesión y
     * al entrar alguien distinto.
     */
    fun vaciar() = bd.enTransaccion { for (tabla in tablas) bd.ejecutar("DELETE FROM $tabla") }

    fun duenoActual(): String? =
        bd.consultar("SELECT valor FROM estado_texto WHERE clave = 'dueno'") { it.texto(0) }
            .firstOrNull()

    fun fijarDueno(valor: String) =
        bd.ejecutar(
            """
            INSERT INTO estado_texto (clave, valor) VALUES ('dueno', ?)
            ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor
            """,
            valor,
        )

    /**
     * Enlaces visibles, sin lápidas: lo que cuenta para preguntar si se quieren conservar al entrar
     * en una cuenta.
     */
    fun contarElementos(): Int =
        bd.consultar("SELECT COUNT(*) FROM elementos WHERE borrado = 0") { it.entero(0).toInt() }
            .first()

    /**
     * Adopta lo que hay en el teléfono para la cuenta en la que se acaba de entrar: identificadores
     * nuevos y a la cola de subida.
     *
     * Los identificadores nuevos no son un capricho. Si estos enlaces venían de otra cuenta, el
     * servidor ya tiene esos mismos a nombre de su dueño anterior y rechaza el cambio. Antes de
     * darse cuenta de esto, en iOS los enlaces se quedaban en la cola reintentándose para siempre.
     *
     * Las lápidas se tiran en vez de subirse: son el rastro de un borrado que la OTRA cuenta ya
     * conoce, y en esta no significan nada.
     */
    fun adoptarConIdsNuevos(generarId: GeneradorId = ::generarIdUnico) = bd.enTransaccion {
        bd.ejecutar("DELETE FROM elementos WHERE borrado = 1")
        bd.ejecutar("DELETE FROM etiquetas_definidas WHERE borrado = 1")
        // Las colas apuntan a los identificadores viejos: se vacían antes de renumerar, o
        // quedarían señalando a filas que ya no existen.
        bd.ejecutar("DELETE FROM outbox")
        bd.ejecutar("DELETE FROM outbox_etiquetas")

        for (id in bd.consultar("SELECT id FROM elementos") { it.textoObligatorio(0) }) {
            bd.ejecutar("UPDATE elementos SET id = ? WHERE id = ?", generarId(), id)
        }
        for (id in bd.consultar("SELECT id FROM etiquetas_definidas") { it.textoObligatorio(0) }) {
            bd.ejecutar("UPDATE etiquetas_definidas SET id = ? WHERE id = ?", generarId(), id)
        }

        bd.ejecutar("INSERT INTO outbox (id) SELECT id FROM elementos")
        bd.ejecutar("INSERT INTO outbox_etiquetas (id) SELECT id FROM etiquetas_definidas")
    }

    // Cursor de sincronización: el último `servidorEn` recibido

    fun cursor(): MarcaDeTiempo =
        bd.consultar("SELECT valor FROM estado_sincronizacion WHERE clave = 'cursor'") {
                it.entero(0)
            }
            .firstOrNull() ?: 0

    fun fijarCursor(valor: MarcaDeTiempo) =
        bd.ejecutar(
            """
            INSERT INTO estado_sincronizacion (clave, valor) VALUES ('cursor', ?)
            ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor
            """,
            valor,
        )

    // Enlaces

    fun cargarTodos(): Cache =
        bd.consultar("SELECT $columnasElemento FROM elementos", leer = ::elementoDeFila)
            .associateBy { it.id }

    fun cargarPendientes(): Cache =
        bd.consultar(
                "SELECT $columnasElementoConPrefijo FROM elementos e JOIN outbox o ON o.id = e.id",
                leer = ::elementoDeFila,
            )
            .associateBy { it.id }

    /** Guarda el resultado de una fusión. No toca la cola: eso lo decide quien sincroniza. */
    fun guardar(cache: Cache) = bd.enTransaccion { cache.values.forEach(::upsert) }

    /** Un cambio local (alta, edición o baja): se ve al instante y queda encolado. */
    fun marcarPendiente(elemento: Elemento) = bd.enTransaccion {
        upsert(elemento)
        bd.ejecutar("INSERT OR IGNORE INTO outbox (id) VALUES (?)", elemento.id)
    }

    fun limpiarPendientes(ids: List<String>) = bd.enTransaccion {
        ids.forEach { bd.ejecutar("DELETE FROM outbox WHERE id = ?", it) }
    }

    /** Varios cambios locales de golpe, como renombrar una etiqueta en todos sus enlaces. */
    fun marcarPendientes(elementos: List<Elemento>) = bd.enTransaccion {
        elementos.forEach(::marcarPendiente)
    }

    /** Para agrupar varias operaciones del almacén en una sola transacción. */
    fun <T> enTransaccion(bloque: () -> T): T = bd.enTransaccion(bloque)

    private fun upsert(elemento: Elemento) =
        bd.ejecutar(
            """
            INSERT INTO elementos
                (id, url, titulo, descripcion, imagen_url, tipo, etiquetas,
                 creado_en, actualizado_en, borrado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                url = excluded.url, titulo = excluded.titulo,
                descripcion = excluded.descripcion, imagen_url = excluded.imagen_url,
                tipo = excluded.tipo, etiquetas = excluded.etiquetas,
                creado_en = excluded.creado_en, actualizado_en = excluded.actualizado_en,
                borrado = excluded.borrado
            """,
            elemento.id,
            elemento.url,
            elemento.titulo,
            elemento.descripcion,
            elemento.imagenUrl,
            elemento.tipo.valor,
            etiquetasAJson(elemento.etiquetas),
            elemento.creadoEn,
            elemento.actualizadoEn,
            elemento.borrado,
        )

    // Etiquetas reservadas

    fun cargarEtiquetasDefinidas(): CacheEtiquetas =
        bd.consultar("SELECT $columnasEtiqueta FROM etiquetas_definidas", leer = ::etiquetaDeFila)
            .associateBy { it.id }

    fun cargarEtiquetasPendientes(): CacheEtiquetas =
        bd.consultar(
                "SELECT $columnasEtiquetaConPrefijo FROM etiquetas_definidas e " +
                    "JOIN outbox_etiquetas o ON o.id = e.id",
                leer = ::etiquetaDeFila,
            )
            .associateBy { it.id }

    fun guardarEtiquetasDefinidas(cache: CacheEtiquetas) = bd.enTransaccion {
        cache.values.forEach(::upsert)
    }

    fun marcarEtiquetaPendiente(etiqueta: EtiquetaDefinida) = bd.enTransaccion {
        upsert(etiqueta)
        bd.ejecutar("INSERT OR IGNORE INTO outbox_etiquetas (id) VALUES (?)", etiqueta.id)
    }

    fun limpiarEtiquetasPendientes(ids: List<String>) = bd.enTransaccion {
        ids.forEach { bd.ejecutar("DELETE FROM outbox_etiquetas WHERE id = ?", it) }
    }

    private fun upsert(etiqueta: EtiquetaDefinida) =
        bd.ejecutar(
            """
            INSERT INTO etiquetas_definidas (id, nombre, creado_en, actualizado_en, borrado)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nombre = excluded.nombre, creado_en = excluded.creado_en,
                actualizado_en = excluded.actualizado_en, borrado = excluded.borrado
            """,
            etiqueta.id,
            etiqueta.nombre,
            etiqueta.creadoEn,
            etiqueta.actualizadoEn,
            etiqueta.borrado,
        )

    private companion object {
        /**
         * El esquema de `app-windows/guardar_enlaces/almacen_local.py`, copiado tal cual. Si cambia
         * allí, cambia aquí y en iOS; la prueba `PruebasEsquema` lo compara columna por columna con
         * lo que tiene Windows escrito hoy.
         */
        val esquema =
            listOf(
                """
                CREATE TABLE IF NOT EXISTS elementos (
                    id TEXT PRIMARY KEY,
                    url TEXT NOT NULL,
                    titulo TEXT,
                    descripcion TEXT,
                    imagen_url TEXT,
                    tipo TEXT NOT NULL DEFAULT 'enlace',
                    etiquetas TEXT NOT NULL DEFAULT '[]',
                    creado_en INTEGER NOT NULL,
                    actualizado_en INTEGER NOT NULL,
                    borrado INTEGER NOT NULL DEFAULT 0
                )
                """,
                "CREATE TABLE IF NOT EXISTS outbox (id TEXT PRIMARY KEY)",
                """
                CREATE TABLE IF NOT EXISTS etiquetas_definidas (
                    id TEXT PRIMARY KEY,
                    nombre TEXT NOT NULL,
                    creado_en INTEGER NOT NULL,
                    actualizado_en INTEGER NOT NULL,
                    borrado INTEGER NOT NULL DEFAULT 0
                )
                """,
                "CREATE TABLE IF NOT EXISTS outbox_etiquetas (id TEXT PRIMARY KEY)",
                """
                CREATE TABLE IF NOT EXISTS estado_sincronizacion (
                    clave TEXT PRIMARY KEY,
                    valor INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS estado_texto (
                    clave TEXT PRIMARY KEY,
                    valor TEXT NOT NULL
                )
                """,
            )

        val tablas =
            listOf(
                "elementos",
                "outbox",
                "etiquetas_definidas",
                "outbox_etiquetas",
                "estado_sincronizacion",
                "estado_texto",
            )

        // Las columnas se piden por su nombre y en este orden, en vez de `SELECT *`: así leer una
        // fila no depende de en qué orden las creara quien creó la base.
        const val columnasElemento =
            "id, url, titulo, descripcion, imagen_url, tipo, etiquetas, creado_en, " +
                "actualizado_en, borrado"
        const val columnasElementoConPrefijo =
            "e.id, e.url, e.titulo, e.descripcion, e.imagen_url, e.tipo, e.etiquetas, " +
                "e.creado_en, e.actualizado_en, e.borrado"
        const val columnasEtiqueta = "id, nombre, creado_en, actualizado_en, borrado"
        const val columnasEtiquetaConPrefijo =
            "e.id, e.nombre, e.creado_en, e.actualizado_en, e.borrado"

        fun elementoDeFila(fila: BaseDatos.Fila) =
            Elemento(
                id = fila.textoObligatorio(0),
                url = fila.textoObligatorio(1),
                titulo = fila.texto(2),
                descripcion = fila.texto(3),
                imagenUrl = fila.texto(4),
                tipo = TipoElemento.desde(fila.texto(5)),
                etiquetas = etiquetasDesdeJson(fila.texto(6)),
                creadoEn = fila.entero(7),
                actualizadoEn = fila.entero(8),
                borrado = fila.bandera(9),
            )

        fun etiquetaDeFila(fila: BaseDatos.Fila) =
            EtiquetaDefinida(
                id = fila.textoObligatorio(0),
                nombre = fila.textoObligatorio(1),
                creadoEn = fila.entero(2),
                actualizadoEn = fila.entero(3),
                borrado = fila.bandera(4),
            )

        /**
         * Las etiquetas van en una columna de texto como lista JSON, igual que en los otros dos
         * clientes: es el mismo formato leído por gente distinta, y no se cambia por gusto.
         */
        fun etiquetasAJson(etiquetas: List<String>): String = Json.encodeToString(etiquetas)

        fun etiquetasDesdeJson(texto: String?): List<String> =
            try {
                Json.decodeFromString<List<String>>(texto ?: "[]")
            } catch (_: SerializationException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
    }
}
