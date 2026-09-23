package com.jmortizsilva.guardarenlaces.fontaneria

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * La sesión: token de acceso en memoria, token de refresco en el almacén de credenciales.
 *
 * Dos diferencias con la de iOS, y las dos son fallos de allí que aquí no se copian (ninguno está
 * comprobado en el iPhone, están leídos en el código):
 *
 * 1. **Las renovaciones van de una en una.** Cada `POST /auth/renovar` revoca el token de refresco
 *    usado. Si dos peticiones reciben un 401 a la vez (comprobar un enlace y sincronizar al volver
 *    a la app), la segunda renovaría con un token ya revocado, fallaría, y se cerraría la sesión.
 *    En iOS la sesión es un actor, pero un actor deja entrar otra llamada mientras la primera
 *    espera a la red. Aquí hay un cerrojo, y quien llega segundo ve que el token ya cambió y usa el
 *    nuevo.
 * 2. **El token guardado solo se tira si el servidor dice 401**, que es lo que dice el contrato:
 *    «el cliente debe volver a iniciar sesión desde cero». En iOS se tira por cualquier fallo,
 *    también por no tener red: abrir la app sin conexión cerraría la sesión.
 */
class Sesion(
    private val cliente: ClienteApi,
    private val credenciales: AlmacenCredenciales,
) {
    private val cerrojo = Mutex()

    @Volatile
    var tokenAcceso: String? = null
        private set

    @Volatile
    var usuario: UsuarioApi? = null
        private set

    @Volatile private var tokenGuardado: Boolean? = null

    /**
     * Hay cuenta: hay un token de refresco guardado. Puede que todavía no haya token de acceso, por
     * ejemplo al arrancar sin red; se pedirá con la primera petición que lo necesite.
     */
    val conCuenta: Boolean
        get() = tokenGuardado ?: (credenciales.tokenRefresco() != null).also { tokenGuardado = it }

    sealed interface Restauracion {
        data object SinCuenta : Restauracion

        data object Restaurada : Restauracion

        /** Hay cuenta, pero ahora no se puede hablar con el servidor. La cuenta se conserva. */
        data class SinConexion(val fallo: ErrorApi) : Restauracion
    }

    /** Cambia el código que trae el navegador por los tokens de sesión. */
    suspend fun entrar(codigoDeCanje: String) = cerrojo.withLock {
        aplicar(cliente.canjear(codigoDeCanje))
    }

    /** Solo contra un servidor con `PERMITIR_LOGIN_DEV=true`. */
    suspend fun entrarComoDesarrollo(email: String) = cerrojo.withLock {
        aplicar(cliente.loginDeDesarrollo(email))
    }

    /** Recupera la sesión con el token guardado de una vez anterior. */
    suspend fun restaurar(): Restauracion {
        if (!conCuenta) return Restauracion.SinCuenta
        return try {
            renovar(caducado = null)
            Restauracion.Restaurada
        } catch (fallo: ErrorApi) {
            if (conCuenta) Restauracion.SinConexion(fallo) else Restauracion.SinCuenta
        }
    }

    suspend fun cerrar() = cerrojo.withLock {
        credenciales.tokenRefresco()?.let { guardado ->
            // Si el servidor no contesta, la sesión se cierra aquí igual: lo que no puede pasar
            // es que el botón no haga nada.
            try {
                cliente.cerrarSesion(guardado)
            } catch (_: ErrorApi) {}
        }
        olvidar()
    }

    /**
     * Ejecuta algo que necesita el token de acceso. Si no lo hay todavía, lo pide; si el servidor
     * dice que ha caducado, lo renueva una vez y lo vuelve a intentar. Si la renovación también
     * falla, el fallo se propaga: ahí ya toca volver a iniciar sesión.
     */
    suspend fun <T> conReintento(peticion: suspend (String) -> T): T {
        val token = tokenAcceso ?: renovar(caducado = null)
        return try {
            peticion(token)
        } catch (fallo: ErrorApi) {
            if (!fallo.esSesionCaducada) throw fallo
            peticion(renovar(caducado = token))
        }
    }

    /**
     * Un token de acceso nuevo, pidiéndolo solo si hace falta: si mientras se esperaba el cerrojo
     * otra llamada ya lo renovó, se usa ese.
     */
    private suspend fun renovar(caducado: String?): String = cerrojo.withLock {
        tokenAcceso
            ?.takeIf { it != caducado }
            ?.let { yaRenovado ->
                return yaRenovado
            }

        val guardado =
            credenciales.tokenRefresco() ?: throw ErrorApi("no hay ninguna sesión iniciada", 401)
        try {
            aplicar(cliente.renovar(guardado))
        } catch (fallo: ErrorApi) {
            if (fallo.esSesionCaducada) olvidar()
            throw fallo
        }
        checkNotNull(tokenAcceso)
    }

    private fun aplicar(respuesta: RespuestaCanje) {
        // La rotación obliga a guardar el token nuevo antes que nada: el anterior queda revocado
        // en cuanto se usa, así que perder este deja la sesión sin forma de renovarse.
        credenciales.guardarTokenRefresco(respuesta.tokenRefresco)
        tokenGuardado = true
        tokenAcceso = respuesta.tokenAcceso
        respuesta.usuario?.let { usuario = it }
    }

    private fun olvidar() {
        credenciales.borrarTokenRefresco()
        tokenGuardado = false
        tokenAcceso = null
        usuario = null
    }
}
