package com.jmortizsilva.guardarenlaces

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.core.net.toUri
import com.jmortizsilva.guardarenlaces.dominio.Login
import com.jmortizsilva.guardarenlaces.dominio.Proveedor
import com.jmortizsilva.guardarenlaces.dominio.Textos
import kotlinx.coroutines.CompletableDeferred

/**
 * Abre el inicio de sesión en una Auth Tab y espera a que vuelva. Ver «Entrar con una cuenta» en
 * `PLAN.md`.
 *
 * Lo normal es que la vuelta llegue por la propia Auth Tab (`alVolverDeLaAuthTab`): el navegador
 * captura `guardarenlaces://auth-callback` y se la da a la app sin pasar por el sistema, así que
 * ninguna otra app que declare el esquema puede quedarse con el código. Si el navegador no admite
 * Auth Tab, la librería abre una pestaña normal, y ahí la vuelta llega como un enlace a la
 * actividad (`alVolverPorEnlace`). Ese camino **no se ha probado**: el teléfono de pruebas tiene un
 * Chrome que sí la admite.
 *
 * Vive con la actividad, que es quien registra el lanzador. Si Android la destruye mientras el
 * navegador está delante, el inicio de sesión en curso se pierde y hay que volver a pedirlo.
 */
class IniciadorDeSesion {
    lateinit var lanzador: ActivityResultLauncher<Intent>

    private var pendiente: CompletableDeferred<Login.Resultado>? = null
    private var proveedor: Proveedor? = null

    suspend fun pedirCodigo(url: String, proveedor: Proveedor): Login.Resultado {
        val espera = CompletableDeferred<Login.Resultado>()
        pendiente = espera
        this.proveedor = proveedor
        return try {
            AuthTabIntent.Builder().build().launch(lanzador, url.toUri(), Login.ESQUEMA)
            espera.await()
        } finally {
            pendiente = null
            this.proveedor = null
        }
    }

    fun alVolverDeLaAuthTab(resultado: AuthTabIntent.AuthResult) {
        val espera = pendiente ?: return
        val quien = proveedor ?: return
        when (resultado.resultCode) {
            AuthTabIntent.RESULT_OK ->
                espera.complete(Login.leerCallback(resultado.resultUri.toString(), quien))
            // Con la pestaña normal de repuesto, el lanzador también devuelve «cancelado» al
            // cerrarse, y puede hacerlo antes de que llegue el enlace de vuelta con el código. Se
            // da un respiro: si el enlace llega entretanto, gana él, porque `complete` solo vale
            // la primera vez.
            AuthTabIntent.RESULT_CANCELED ->
                Handler(Looper.getMainLooper())
                    .postDelayed(
                        { espera.complete(Login.Resultado.Cancelado) },
                        ESPERA_AL_ENLACE_MS,
                    )
            else -> espera.complete(Login.Resultado.Error(Textos.loginFallido))
        }
    }

    /**
     * La vuelta por enlace, cuando no hubo Auth Tab. Solo se acepta si hay un inicio de sesión en
     * curso, lanzado desde aquí: si no, un enlace de fuera podría meter a alguien en una cuenta
     * ajena. Devuelve si la ha atendido.
     */
    fun alVolverPorEnlace(enlace: Uri): Boolean {
        val espera = pendiente ?: return false
        val quien = proveedor ?: return false
        espera.complete(Login.leerCallback(enlace.toString(), quien))
        return true
    }

    private companion object {
        const val ESPERA_AL_ENLACE_MS = 700L
    }
}
