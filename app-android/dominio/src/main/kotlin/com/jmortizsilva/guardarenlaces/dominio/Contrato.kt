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
 * Al escribir, dos reglas que copian lo que hace iOS:
 * - `encodeDefaults`: `borrado: false` o `etiquetas: []` se mandan. Son datos para el servidor, no
 *   algo que se pueda dar por supuesto.
 * - `explicitNulls = false`: lo que es nulo no se manda, en vez de mandar `null`, que es lo que
 *   hace el `JSONEncoder` de Swift. Hoy el servidor trata igual las dos cosas (`COALESCE`: conserva
 *   lo que había), así que no cambia el resultado; se hace para que Android mande lo mismo que el
 *   iPhone y no dependa de ese detalle del servidor.
 */
val jsonDelContrato = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}
