package com.jmortizsilva.guardarenlaces.dominio

/**
 * Identificadores predecibles (`id-de-prueba-1`, `-2`...). Sin esto habría que comparar contra uuid
 * aleatorios, que no se pueden comprobar.
 */
fun generadorSecuencial(): GeneradorId {
    var contador = 0
    return { "id-de-prueba-${++contador}" }
}

fun relojFijo(instante: MarcaDeTiempo): Reloj = { instante }

/** Decodifica un JSON escrito a mano en la prueba, como lo haría la app con el del servidor. */
inline fun <reified T> desdeJson(json: String): T = jsonDelContrato.decodeFromString<T>(json)

/** Codifica y vuelve a decodificar: el viaje al JSON del contrato no pierde nada. */
inline fun <reified T> idaYVuelta(valor: T): T =
    jsonDelContrato.decodeFromString<T>(jsonDelContrato.encodeToString(valor))
