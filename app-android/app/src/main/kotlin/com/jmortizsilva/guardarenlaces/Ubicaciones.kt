package com.jmortizsilva.guardarenlaces

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.jmortizsilva.guardarenlaces.fontaneria.AlmacenLocal
import java.io.File

/**
 * Dónde vive cada cosa en el teléfono. Las reglas de la copia de seguridad
 * (`res/xml/reglas_copia.xml` y `res/xml/reglas_copia_antigua.xml`) nombran estas mismas rutas: si
 * cambian aquí, cambian allí.
 */
object Ubicaciones {
    const val NOMBRE_BASE_DATOS = "guardalo.db"

    /** En `files/sesion/`, una carpeta propia para poder dejarla fuera de la copia de seguridad. */
    fun ficheroCredenciales(contexto: Context) = File(File(contexto.filesDir, "sesion"), "token")

    /**
     * El SQLite del propio teléfono, sin librería nativa añadida. Las pruebas del Mac usan otro
     * conductor con el mismo SQL; que este funcione se comprueba en las pruebas instrumentadas.
     */
    fun abrirAlmacen(contexto: Context): AlmacenLocal {
        val ruta = contexto.getDatabasePath(NOMBRE_BASE_DATOS)
        // SQLite no crea carpetas: sin esto, la primera vez falla al abrir y no se entiende por
        // qué.
        ruta.parentFile?.mkdirs()
        return AlmacenLocal(AndroidSQLiteDriver(), ruta.path)
    }
}
