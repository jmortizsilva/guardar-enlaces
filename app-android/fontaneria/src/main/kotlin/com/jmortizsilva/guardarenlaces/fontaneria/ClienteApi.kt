package com.jmortizsilva.guardarenlaces.fontaneria

import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.EtiquetaDefinida
import com.jmortizsilva.guardarenlaces.dominio.Login
import com.jmortizsilva.guardarenlaces.dominio.MarcaDeTiempo
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.Proveedor
import com.jmortizsilva.guardarenlaces.dominio.TipoElemento
import com.jmortizsilva.guardarenlaces.dominio.jsonDelContrato
import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Lo que responde el servidor al canjear un código o al renovar la sesión. */
@Serializable
data class RespuestaCanje(
    val tokenAcceso: String,
    val expiraEn: MarcaDeTiempo,
    val tokenRefresco: String,
    val usuario: UsuarioApi? = null,
)

@Serializable data class UsuarioApi(val id: Long, val email: String, val proveedor: String)

@Serializable
data class RespuestaMetadatos(
    val titulo: String? = null,
    val descripcion: String? = null,
    val imagenUrl: String? = null,
    val tipo: String = "enlace",
) {
    val comoMetadatos
        get() = MetadatosExtraidos(titulo, descripcion, imagenUrl, TipoElemento.desde(tipo))
}

/**
 * Una entrada que el servidor no ha aplicado. Hay que sacarla de la cola igualmente: no se va a
 * aceptar por mucho que se insista, y dejarla dentro reenvía el lote entero en cada sincronización,
 * para siempre.
 */
@Serializable
data class EntradaRechazada(
    val id: String,
    /** `sin_url`, `sin_nombre` o `no_aplicable` (ver CONTRATO-API.md). */
    val motivo: String,
)

@Serializable
data class RespuestaPush(
    val elementos: List<Elemento> = emptyList(),
    val rechazados: List<EntradaRechazada> = emptyList(),
    val etiquetasDefinidas: List<EtiquetaDefinida> = emptyList(),
    val etiquetasRechazadas: List<EntradaRechazada> = emptyList(),
)

@Serializable
data class RespuestaSincronizar(
    val elementos: List<Elemento> = emptyList(),
    val etiquetasDefinidas: List<EtiquetaDefinida> = emptyList(),
    val servidorEn: MarcaDeTiempo = 0,
    val masDisponible: Boolean = false,
)

/**
 * Un fallo hablando con el servidor, ya traducido a algo que se puede leer en voz alta. `codigo` es
 * el estado HTTP cuando lo hay; sin él, es que no se llegó a hablar con nadie.
 */
class ErrorApi(val mensaje: String, val codigo: Int?) : Exception(mensaje) {
    /** El token de acceso ha caducado y toca renovarlo. */
    val esSesionCaducada
        get() = codigo == 401
}

/**
 * Cliente HTTP del backend. El contrato completo está en `backend/docs/CONTRATO-API.md`.
 *
 * No decide nada: traduce llamadas a peticiones y respuestas a tipos. Quién reintenta y cuándo es
 * cosa de `Sesion`.
 */
class ClienteApi(urlBase: String, private val http: OkHttpClient = clienteHttpPorDefecto) {
    private val base: HttpUrl =
        requireNotNull(urlBase.trimEnd('/').toHttpUrlOrNull()) {
            "la dirección del servidor no es válida: $urlBase"
        }

    // Autenticación

    /**
     * Dirección con la que arranca el inicio de sesión. No se pide desde aquí: se abre en el
     * navegador, y el servidor responde con una redirección al consentimiento del proveedor.
     */
    fun urlIniciarLogin(proveedor: Proveedor, estado: String): String =
        ruta("auth/iniciar")
            .newBuilder()
            .addQueryParameter("proveedor", proveedor.valor)
            .addQueryParameter("modo", "deeplink")
            .addQueryParameter("estado", estado)
            .addQueryParameter("esquema", Login.ESQUEMA)
            .build()
            .toString()

    /** Cambia el código de canje (un solo uso, unos 60 segundos de vida) por los tokens. */
    suspend fun canjear(codigoCanje: String): RespuestaCanje =
        pedir("auth/canjear", mapOf("codigoCanje" to codigoCanje))

    /** Solo contra un servidor con `PERMITIR_LOGIN_DEV=true`. */
    suspend fun loginDeDesarrollo(email: String): RespuestaCanje =
        pedir("auth/dev-login", mapOf("email" to email))

    suspend fun renovar(tokenRefresco: String): RespuestaCanje =
        pedir("auth/renovar", mapOf("tokenRefresco" to tokenRefresco))

    suspend fun cerrarSesion(tokenRefresco: String) {
        pedir<Map<String, String>, Nada>("auth/logout", mapOf("tokenRefresco" to tokenRefresco))
    }

    // Metadatos

    suspend fun metadatos(url: String, tokenAcceso: String): RespuestaMetadatos =
        pedir("metadatos", mapOf("url" to url), tokenAcceso)

    // Sincronización

    suspend fun bajarCambios(
        desde: MarcaDeTiempo,
        tokenAcceso: String,
        limite: Int = 300,
    ): RespuestaSincronizar {
        val url =
            ruta("sincronizar")
                .newBuilder()
                .addQueryParameter("desde", desde.toString())
                .addQueryParameter("limite", limite.toString())
                .build()
        val peticion =
            Request.Builder().url(url).get().header("Authorization", "Bearer $tokenAcceso").build()
        return enviar(peticion, serializer())
    }

    @Serializable
    private class CuerpoPush(
        val elementos: List<Elemento>? = null,
        val etiquetasDefinidas: List<EtiquetaDefinida>? = null,
    )

    suspend fun subirCambios(
        elementos: List<Elemento>,
        etiquetas: List<EtiquetaDefinida>,
        tokenAcceso: String,
    ): RespuestaPush =
        // Los dos son opcionales, pero hace falta al menos uno: se manda solo lo que hay, para no
        // enviar listas vacías.
        pedir(
            "sincronizar",
            CuerpoPush(elementos.ifEmpty { null }, etiquetas.ifEmpty { null }),
            tokenAcceso,
        )

    // Por dentro

    /** Para las respuestas que no traen nada que leer, como cerrar sesión. */
    @Serializable private class Nada

    private fun ruta(camino: String): HttpUrl = base.newBuilder().addPathSegments(camino).build()

    private suspend inline fun <reified C, reified T> pedir(
        camino: String,
        cuerpo: C,
        tokenAcceso: String? = null,
    ): T {
        val json = jsonDelContrato.encodeToString(serializer<C>(), cuerpo)
        val peticion =
            Request.Builder()
                .url(ruta(camino))
                .post(json.toRequestBody(tipoJson))
                .apply { if (tokenAcceso != null) header("Authorization", "Bearer $tokenAcceso") }
                .build()
        return enviar(peticion, serializer())
    }

    private suspend fun <T> enviar(peticion: Request, lector: KSerializer<T>): T {
        val (codigo, cuerpo) =
            try {
                http.newCall(peticion).ejecutar()
            } catch (_: IOException) {
                // Los mensajes de OkHttp llegan en inglés y contando cosas de red que aquí no
                // ayudan. Esta frase acaba leída en voz alta, sola («Sin conexión con el
                // servidor») o detrás de la acción que falló («No se pudo sincronizar: sin
                // conexión con el servidor»); por eso va en minúscula y sin verbo.
                throw ErrorApi("sin conexión con el servidor", null)
            }

        if (codigo !in 200..299) throw ErrorApi(mensajeDeError(cuerpo, codigo), codigo)
        return try {
            jsonDelContrato.decodeFromString(lector, cuerpo.ifBlank { "{}" })
        } catch (_: SerializationException) {
            throw ErrorApi("el servidor respondió algo que no se entiende", codigo)
        } catch (_: IllegalArgumentException) {
            throw ErrorApi("el servidor respondió algo que no se entiende", codigo)
        }
    }

    @Serializable private class CuerpoError(val error: String? = null)

    /** El cuerpo de error del contrato es `{"error": "..."}`. */
    private fun mensajeDeError(cuerpo: String, codigo: Int): String {
        val detalle =
            try {
                jsonDelContrato.decodeFromString<CuerpoError>(cuerpo).error
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        return detalle?.ifBlank { null } ?: "el servidor respondió con un error $codigo"
    }

    private companion object {
        val tipoJson = "application/json".toMediaType()
    }
}
