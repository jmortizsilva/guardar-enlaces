package com.jmortizsilva.guardarenlaces

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.EtiquetaDefinida
import com.jmortizsilva.guardarenlaces.fontaneria.AlmacenLocal
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo que solo existe en el teléfono: el Keystore y el SQLite del sistema. Todo lo demás se prueba
 * en el Mac; esto corre con `./gradlew connectedDebugAndroidTest` y el teléfono conectado.
 *
 * Usa su propia clave y sus propios ficheros, con nombres de prueba: no toca la sesión ni los
 * enlaces de quien tenga la app instalada.
 */
@RunWith(AndroidJUnit4::class)
class PruebasCredencialesKeystore {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val fichero = File(contexto.cacheDir, "prueba-sesion/token")
    private val alias = "guardalo-prueba-${System.nanoTime()}"
    private val credenciales = CredencialesKeystore(fichero, alias)

    @After
    fun limpiar() {
        credenciales.borrarTokenRefresco()
        credenciales.borrarClave()
        fichero.parentFile?.deleteRecursively()
    }

    @Test
    fun el_token_vuelve_tal_cual_aunque_se_abra_otra_vez() {
        credenciales.guardarTokenRefresco("refresco-ñ-😀")

        // Otra instancia, sin nada en memoria: lo que lee sale de verdad del fichero y la clave.
        assertEquals("refresco-ñ-😀", CredencialesKeystore(fichero, alias).tokenRefresco())
    }

    @Test
    fun en_el_fichero_no_esta_el_token_en_claro() {
        credenciales.guardarTokenRefresco("refresco-que-no-debe-verse")

        assertFalse(String(fichero.readBytes(), Charsets.ISO_8859_1).contains("refresco-que-no"))
    }

    @Test
    fun guardar_otro_sustituye_al_anterior_por_la_rotacion() {
        credenciales.guardarTokenRefresco("primero")
        credenciales.guardarTokenRefresco("segundo")

        assertEquals("segundo", CredencialesKeystore(fichero, alias).tokenRefresco())
    }

    @Test
    fun borrar_se_lo_lleva() {
        credenciales.guardarTokenRefresco("algo")
        credenciales.borrarTokenRefresco()

        assertNull(CredencialesKeystore(fichero, alias).tokenRefresco())
        assertFalse(fichero.exists())
    }

    @Test
    fun un_fichero_roto_se_tira_y_cuenta_como_sin_sesion() {
        credenciales.guardarTokenRefresco("algo")
        fichero.writeBytes(byteArrayOf(12, 1, 2, 3))

        assertNull(CredencialesKeystore(fichero, alias).tokenRefresco())
        assertFalse(fichero.exists())
    }

    @Test
    fun sin_la_clave_el_fichero_no_sirve_y_se_tira() {
        // Lo que pasa si se restaurara el fichero en otro teléfono, o si Android invalidara la
        // clave: no hay forma de leerlo nunca más.
        credenciales.guardarTokenRefresco("algo")
        credenciales.borrarClave()

        assertNull(CredencialesKeystore(fichero, alias).tokenRefresco())
        assertFalse(fichero.exists())
    }
}

@RunWith(AndroidJUnit4::class)
class PruebasBaseDatosDelTelefono {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val ruta = File(contexto.cacheDir, "prueba-${System.nanoTime()}.db")

    @After
    fun limpiar() {
        for (sufijo in listOf("", "-wal", "-shm")) File(ruta.path + sufijo).delete()
    }

    private fun abrir() = AlmacenLocal(androidx.sqlite.driver.AndroidSQLiteDriver(), ruta.path)

    @Test
    fun con_el_sqlite_del_sistema_un_enlace_vuelve_tal_cual() {
        val original =
            Elemento(
                id = "e1",
                url = "https://a.com/artículo",
                titulo = "Café & teoría",
                etiquetas = listOf("ocio", "pendiente"),
                creadoEn = 50,
                actualizadoEn = 100,
            )
        abrir().apply {
            marcarPendiente(original)
            marcarEtiquetaPendiente(EtiquetaDefinida(id = "t1", nombre = "casa"))
            fijarCursor(900)
            cerrar()
        }

        val almacen = abrir()
        assertEquals(original, almacen.cargarTodos()["e1"])
        assertEquals(setOf("e1"), almacen.cargarPendientes().keys)
        assertEquals("casa", almacen.cargarEtiquetasDefinidas()["t1"]?.nombre)
        assertEquals(900L, almacen.cursor())
        almacen.cerrar()
    }

    @Test
    fun la_base_queda_en_modo_wal() {
        // Se mira el fichero `-wal` con la base abierta, y no el PRAGMA desde otra conexión: el
        // conductor del sistema, al abrir, vuelve a poner el modo por defecto de Android
        // (`truncate`), así que otra conexión no dice nada de la del almacén.
        val almacen = abrir()
        almacen.fijarCursor(1)

        assertTrue(File(ruta.path + "-wal").exists())
        almacen.cerrar()
    }

    @Test
    fun la_ruta_de_verdad_se_puede_abrir_desde_cero() {
        // `databases/` no existe en una instalación nueva y SQLite no crea carpetas.
        val almacen = Ubicaciones.abrirAlmacen(contexto)
        assertTrue(contexto.getDatabasePath(Ubicaciones.NOMBRE_BASE_DATOS).exists())
        almacen.cerrar()
    }
}
