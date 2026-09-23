package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PruebasLeerCallback {
    private fun leer(texto: String, proveedor: Proveedor = Proveedor.Google) =
        Login.leerCallback(texto, proveedor)

    @Test
    fun `con codigo, es un inicio de sesion que fue bien`() {
        assertEquals(
            Login.Resultado.Exito("abc123"),
            leer("guardarenlaces://auth-callback?codigo=abc123"),
        )
    }

    @Test
    fun `sin correo, lo dice con sus palabras y no con el motivo del contrato`() {
        assertEquals(
            Login.Resultado.Error(
                "Tu cuenta no ha dado ningún correo, y hace falta para crear la cuenta."
            ),
            leer("guardarenlaces://auth-callback?error=sin_email"),
        )
    }

    @Test
    fun `si el proveedor rechazo el intercambio, dice cual e invita a repetir`() {
        assertEquals(
            Login.Resultado.Error("Google rechazó el inicio de sesión. Vuelve a intentarlo."),
            leer("guardarenlaces://auth-callback?error=fallo_intercambio"),
        )
        // En iOS esto no puede pasar, porque Apple no va por web. Aquí sí, y decir «Google»
        // a quien estaba entrando con Apple es mandarle a mirar en el sitio equivocado.
        assertEquals(
            Login.Resultado.Error("Apple rechazó el inicio de sesión. Vuelve a intentarlo."),
            leer("guardarenlaces://auth-callback?error=fallo_intercambio", Proveedor.Apple),
        )
    }

    @Test
    fun `un motivo que no se conoce no deja al usuario sin explicacion`() {
        assertEquals(
            Login.Resultado.Error("No se pudo iniciar sesión."),
            leer("guardarenlaces://auth-callback?error=algo_nuevo"),
        )
    }

    @Test
    fun `una vuelta sin nada tampoco se da por buena`() {
        assertEquals(
            Login.Resultado.Error("No se pudo iniciar sesión."),
            leer("guardarenlaces://auth-callback"),
        )
    }

    @Test
    fun `un codigo vacio no cuenta como haber entrado`() {
        assertEquals(
            Login.Resultado.Error("No se pudo iniciar sesión."),
            leer("guardarenlaces://auth-callback?codigo="),
        )
    }

    @Test
    fun `si el codigo viene dos veces vale el primero`() {
        assertEquals(
            Login.Resultado.Exito("uno"),
            leer("guardarenlaces://auth-callback?codigo=uno&codigo=dos"),
        )
    }
}

class PruebasEstadoLogin {
    @Test
    fun `son 32 caracteres hexadecimales`() {
        val estado = Login.generarEstado()

        assertEquals(32, estado.length)
        assertTrue(estado.all { it in "0123456789abcdef" })
    }

    @Test
    fun `dos seguidos no se repiten`() {
        assertNotEquals(Login.generarEstado(), Login.generarEstado())
    }

    @Test
    fun `se puede fijar para probarlo`() {
        assertEquals(
            "ab".repeat(16),
            Login.generarEstado { cuantos -> ByteArray(cuantos) { 0xAB.toByte() } },
        )
    }
}
