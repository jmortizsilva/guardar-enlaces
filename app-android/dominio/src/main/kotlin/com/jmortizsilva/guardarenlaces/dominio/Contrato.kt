package com.jmortizsilva.guardarenlaces.dominio

import kotlinx.serialization.json.Json

/**
 * Cómo se lee y se escribe el JSON de `backend/docs/CONTRATO-API.md`.
 *
 * Tolerante al leer, por lo mismo que los `init(from:)` de iOS: una baja que llega del servidor
 * solo trae identificador y fecha, y el contrato crece añadiendo campos y tipos, nunca quitándolos.
 * - `ignoreUnknownKeys`: un campo nuevo del servidor no invalida el elemento.
 * - `coerceInputValues`: un `null` donde no cabe, o un tipo que esta versión no conoce, se queda
 *   con el valor por defecto en vez de tirar el elemento entero.
 *
 * Y completo al escribir (`encodeDefaults`): `borrado: false` o `etiquetas: []` son datos para el
 * servidor, no algo que se pueda dar por supuesto.
 */
val jsonDelContrato = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}
