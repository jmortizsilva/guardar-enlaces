package com.jmortizsilva.guardarenlaces.dominio

/** Los enlaces que ya había en el teléfono cuando alguien inicia sesión. */
data class EnlacesEnElTelefono(
    val cuantos: Int,
    /** `true` si son de otra cuenta; `false` si se guardaron sin cuenta. */
    val deOtraCuenta: Boolean,
)

/** Qué hacer con lo que ya hay guardado. */
enum class AsientoDeCuenta {
    /**
     * Darles identificadores nuevos y subirlos a esta cuenta. Los nuevos identificadores no son un
     * capricho: si venían de otra cuenta, el servidor ya tiene esos mismos a nombre de su dueño
     * anterior y rechaza el cambio, así que se quedarían reintentándose para siempre.
     */
    AdoptarLoQueHay,

    /** Borrarlos y empezar limpio con esta cuenta. */
    EmpezarDeCero,
}

/**
 * El primer paso al entrar: puede que no haya nada que hacer, que esté claro qué hacer, o que lo
 * tenga que decidir quien está delante.
 */
sealed interface PasoAlEntrar {
    /** La cuenta de siempre: no se pregunta ni se toca nada. Es el caso normal. */
    data object NoHacerNada : PasoAlEntrar

    data class Asentar(val asiento: AsientoDeCuenta) : PasoAlEntrar

    data class Preguntar(val enlaces: EnlacesEnElTelefono) : PasoAlEntrar
}

/**
 * La caché local no está separada por cuenta, así que al entrar hay que resolver de quién es lo que
 * hay: o se importa a la cuenta nueva, o se borra.
 *
 * Es una función pura, como en iOS: aquí se decide qué hay que hacer, y hacerlo es cosa de quien
 * tiene el almacén delante. Se prueba entera sin simulacros.
 */
object AsentarCuenta {
    /**
     * El «dueño» de la caché. Lleva la dirección del servidor además del correo: el mismo correo en
     * el servidor de pruebas y en el de verdad son dos bibliotecas distintas, y mezclarlas sería
     * peor que empezar de cero.
     */
    fun identidadDueno(urlServidor: String, email: String) = "$urlServidor|$email"

    fun alEntrar(duenoAnterior: String?, dueno: String, cuantosElementos: Int): PasoAlEntrar =
        when {
            duenoAnterior == dueno -> PasoAlEntrar.NoHacerNada
            cuantosElementos == 0 -> PasoAlEntrar.Asentar(AsientoDeCuenta.EmpezarDeCero)
            // Se pregunta en vez de decidirlo aquí porque las dos respuestas son razonables y
            // ninguna de las dos se puede deshacer.
            else ->
                PasoAlEntrar.Preguntar(
                    EnlacesEnElTelefono(cuantosElementos, deOtraCuenta = duenoAnterior != null)
                )
        }

    fun asiento(segunRespuesta: Boolean): AsientoDeCuenta =
        if (segunRespuesta) AsientoDeCuenta.AdoptarLoQueHay else AsientoDeCuenta.EmpezarDeCero
}
