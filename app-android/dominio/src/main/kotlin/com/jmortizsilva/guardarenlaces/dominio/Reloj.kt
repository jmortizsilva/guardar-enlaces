package com.jmortizsilva.guardarenlaces.dominio

import java.util.UUID

/**
 * Milisegundos desde el epoch, como `Date.now()` en JavaScript.
 *
 * La unidad es parte del contrato de sincronización: el backend y los tres clientes comparan sus
 * `actualizadoEn` entre sí para decidir qué cambio gana. Basta con que uno cuente en segundos para
 * que la comparación dé siempre el mismo ganador, y en silencio.
 */
typealias MarcaDeTiempo = Long

/**
 * El reloj se inyecta en todo lo que dependa del tiempo. Así los plazos y los vencimientos se
 * prueban sin esperar de verdad.
 */
typealias Reloj = () -> MarcaDeTiempo

fun relojDelSistema(): MarcaDeTiempo = System.currentTimeMillis()

/** Quién genera los identificadores. Se inyecta por el mismo motivo que el reloj. */
typealias GeneradorId = () -> String

/**
 * En minúsculas, como los generan los otros clientes. `UUID.toString()` ya los da así en Java, pero
 * se deja escrito: en Swift no era así, y el mismo identificador escrito de dos formas deja de
 * encontrarse sin avisar.
 */
fun generarIdUnico(): String = UUID.randomUUID().toString().lowercase()
