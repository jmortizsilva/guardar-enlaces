package com.jmortizsilva.guardarenlaces.fontaneria

/**
 * Dónde se guarda el token de refresco. En el teléfono es el Keystore (`CredencialesKeystore`, en
 * la app); en las pruebas, uno en memoria. Es lo que permite probar `Sesion` entera sin Android.
 *
 * El token de ACCESO no se guarda: dura poco y vive solo en memoria mientras la app está abierta.
 */
interface AlmacenCredenciales {
    fun guardarTokenRefresco(token: String)

    fun tokenRefresco(): String?

    fun borrarTokenRefresco()
}
