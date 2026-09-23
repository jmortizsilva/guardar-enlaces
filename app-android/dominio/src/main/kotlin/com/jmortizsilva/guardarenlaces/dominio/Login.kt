package com.jmortizsilva.guardarenlaces.dominio

import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom

/** Con quién se entra. `valor` es lo que va en `/auth/iniciar?proveedor=`. */
enum class Proveedor(val valor: String, val nombre: String) {
    Google("google", "Google"),
    Apple("apple", "Apple"),
}

/**
 * Entrar con un proveedor, según el contrato del backend.
 *
 * En Android los dos proveedores van por web: la app abre `/auth/iniciar` del servidor en una Auth
 * Tab, el servidor hace todo el intercambio y devuelve el navegador a
 * `guardarenlaces://auth-callback?codigo=…`. Ese código es de un solo uso y dura un minuto;
 * cambiarlo por los tokens es cosa de la sesión.
 *
 * No hay nonce de Apple, que en iOS sí: solo hace falta para el inicio de sesión nativo de Apple
 * (`/auth/apple-nativo`), y en Android no existe.
 */
object Login {
    const val ESQUEMA = "guardarenlaces"

    sealed interface Resultado {
        data class Exito(val codigoDeCanje: String) : Resultado

        /**
         * Cerró el navegador o rechazó el permiso. No es una avería, y la pantalla lo cuenta
         * distinto.
         */
        data object Cancelado : Resultado

        data class Error(val mensaje: String) : Resultado
    }

    private val azar = SecureRandom()

    private fun bytesAleatorios(cuantos: Int) = ByteArray(cuantos).also(azar::nextBytes)

    /**
     * Cadena opaca de un solo uso que ata la vuelta del callback a esta petición concreta. 16 bytes
     * en hexadecimal.
     */
    fun generarEstado(aleatorio: (Int) -> ByteArray = ::bytesAleatorios): String =
        aleatorio(16).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /**
     * Traduce los motivos de error del contrato a algo que se pueda leer en voz alta.
     *
     * Dice el proveedor con el que se estaba entrando. En iOS dice siempre Google porque allí Apple
     * no pasa por aquí; en Android pasan los dos.
     */
    fun mensajeDeError(motivo: String, proveedor: Proveedor): String =
        when (motivo) {
            "sin_email" -> Textos.loginSinCorreo
            "fallo_intercambio" -> Textos.loginRechazado(proveedor.nombre)
            else -> Textos.loginFallido
        }

    /**
     * Lee la URL de vuelta del callback.
     *
     * Se leen solo los parámetros, sin mirar el anfitrión ni la ruta: el esquema es propio de la
     * app y lo único que importa es qué trae. Si un parámetro viene dos veces, vale el primero.
     */
    fun leerCallback(url: String, proveedor: Proveedor): Resultado {
        val consulta =
            try {
                URI(url).rawQuery
            } catch (_: URISyntaxException) {
                null
            }
        val parametros = PartesDeUrl.leer("x:/?${consulta.orEmpty()}")?.parametros.orEmpty()
        val porNombre = parametros.reversed().associate { it.first to it.second.orEmpty() }

        val codigo = porNombre["codigo"]
        if (!codigo.isNullOrEmpty()) return Resultado.Exito(codigo)
        return Resultado.Error(mensajeDeError(porNombre["error"].orEmpty(), proveedor))
    }
}
