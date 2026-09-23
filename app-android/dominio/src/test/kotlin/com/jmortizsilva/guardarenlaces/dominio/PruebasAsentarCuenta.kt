package com.jmortizsilva.guardarenlaces.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private val cuenta = AsentarCuenta.identidadDueno("https://api.ejemplo.com", "persona@ejemplo.com")
private val otraCuenta = AsentarCuenta.identidadDueno("https://api.ejemplo.com", "otra@ejemplo.com")

class PruebasAsentarCuenta {
    @Test
    fun `entrar en la cuenta de siempre no pregunta ni toca nada`() {
        assertEquals(
            PasoAlEntrar.NoHacerNada,
            AsentarCuenta.alEntrar(duenoAnterior = cuenta, dueno = cuenta, cuantosElementos = 12),
        )
    }

    @Test
    fun `con el telefono vacio no se pregunta, no hay nada que decidir`() {
        assertEquals(
            PasoAlEntrar.Asentar(AsientoDeCuenta.EmpezarDeCero),
            AsentarCuenta.alEntrar(duenoAnterior = null, dueno = cuenta, cuantosElementos = 0),
        )
    }

    @Test
    fun `lo guardado sin cuenta se pregunta, diciendo que no era de otra`() {
        assertEquals(
            PasoAlEntrar.Preguntar(EnlacesEnElTelefono(3, deOtraCuenta = false)),
            AsentarCuenta.alEntrar(duenoAnterior = null, dueno = cuenta, cuantosElementos = 3),
        )
    }

    @Test
    fun `al cambiar de cuenta se avisa de que lo de antes era de otra`() {
        assertEquals(
            PasoAlEntrar.Preguntar(EnlacesEnElTelefono(5, deOtraCuenta = true)),
            AsentarCuenta.alEntrar(
                duenoAnterior = otraCuenta,
                dueno = cuenta,
                cuantosElementos = 5,
            ),
        )
    }

    @Test
    fun `decir que si importa con identificadores nuevos, decir que no borra`() {
        assertEquals(AsientoDeCuenta.AdoptarLoQueHay, AsentarCuenta.asiento(segunRespuesta = true))
        assertEquals(AsientoDeCuenta.EmpezarDeCero, AsentarCuenta.asiento(segunRespuesta = false))
    }

    @Test
    fun `el mismo correo en otro servidor es otra biblioteca`() {
        assertNotEquals(
            AsentarCuenta.identidadDueno("http://192.168.1.10:8090", "a@b.com"),
            AsentarCuenta.identidadDueno("https://api.ejemplo.com", "a@b.com"),
        )
    }
}
