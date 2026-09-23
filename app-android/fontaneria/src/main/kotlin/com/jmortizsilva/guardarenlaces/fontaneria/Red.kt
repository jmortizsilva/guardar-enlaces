package com.jmortizsilva.guardarenlaces.fontaneria

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Diez segundos, lo mismo que iOS y que esperaba la app de Expo. Más que eso, en un móvil, es
 * tiempo mirando una pantalla que no dice nada.
 *
 * Uno solo para toda la app: OkHttp reutiliza conexiones e hilos dentro de cada cliente, y crear
 * uno por petición los tira cada vez.
 */
val clienteHttpPorDefecto: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

/**
 * Lanza la petición sin bloquear el hilo y devuelve el código y el cuerpo. Si quien espera se
 * cancela (se cierra la pantalla), se cancela también la petición.
 *
 * El cuerpo se lee hasta `limite` caracteres como mucho: una página enorme leída entera para sacar
 * su título se come la memoria de un teléfono modesto, y el título está al principio.
 */
internal suspend fun Call.ejecutar(limite: Long = Long.MAX_VALUE): Pair<Int, String> =
    suspendCancellableCoroutine { continuacion ->
        continuacion.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuacion.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val resultado =
                        try {
                            response.use { it.code to leerCuerpo(it, limite) }
                        } catch (fallo: IOException) {
                            continuacion.resumeWithException(fallo)
                            return
                        }
                    continuacion.resume(resultado)
                }
            }
        )
    }

private fun leerCuerpo(respuesta: Response, limite: Long): String {
    if (limite == Long.MAX_VALUE) return respuesta.body.string()
    val fuente = respuesta.body.source()
    fuente.request(limite)
    val disponible = minOf(fuente.buffer.size, limite)
    val juego = respuesta.body.contentType()?.charset() ?: Charsets.UTF_8
    return fuente.buffer.readString(disponible, juego)
}
