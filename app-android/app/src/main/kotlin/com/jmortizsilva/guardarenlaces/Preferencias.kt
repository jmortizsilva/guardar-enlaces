package com.jmortizsilva.guardarenlaces

import android.content.Context
import androidx.core.content.edit

/**
 * Lo que la app recuerda entre arranques y no es un enlace ni la sesión. Nada secreto: va sin
 * cifrar, en las preferencias normales de la app.
 */
class Preferencias(contexto: Context) {
    private val guardadas = contexto.getSharedPreferences("preferencias", Context.MODE_PRIVATE)

    /** Si ya se eligió algo en la bienvenida, para no volver a enseñarla en cada arranque. */
    var bienvenidaVista: Boolean
        get() = guardadas.getBoolean(CLAVE_BIENVENIDA_VISTA, false)
        set(valor) = guardadas.edit { putBoolean(CLAVE_BIENVENIDA_VISTA, valor) }

    /** Si al compartir un enlace se guarda directamente, sin la pantalla de las etiquetas. */
    var guardarSinPreguntar: Boolean
        get() = guardadas.getBoolean(CLAVE_GUARDAR_SIN_PREGUNTAR, false)
        set(valor) = guardadas.edit { putBoolean(CLAVE_GUARDAR_SIN_PREGUNTAR, valor) }

    private companion object {
        const val CLAVE_BIENVENIDA_VISTA = "bienvenidaVista"
        const val CLAVE_GUARDAR_SIN_PREGUNTAR = "guardarSinPreguntar"
    }
}
