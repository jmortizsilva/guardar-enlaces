package com.jmortizsilva.guardarenlaces.dominio

import kotlinx.serialization.Serializable

/**
 * Una etiqueta que existe por sí sola, sin que ningún enlace la lleve todavía: sirve para crearla
 * en un dispositivo y verla en los demás antes de usarla.
 *
 * Se sincroniza igual que un elemento (identificador del cliente, lápida, gana la fecha más
 * reciente), en la misma llamada y bajo la clave `etiquetasDefinidas` del contrato. Constructor y
 * `copy` privados por lo mismo que en `Elemento`.
 */
@ConsistentCopyVisibility
@Serializable
data class EtiquetaDefinida
private constructor(
    val id: String,
    val nombre: String = "",
    val creadoEn: MarcaDeTiempo = 0,
    val actualizadoEn: MarcaDeTiempo = 0,
    val borrado: Boolean = false,
) {
    companion object {
        operator fun invoke(
            id: String,
            nombre: String,
            creadoEn: MarcaDeTiempo = 0,
            actualizadoEn: MarcaDeTiempo = 0,
            borrado: Boolean = false,
        ) = EtiquetaDefinida(id, nombre, creadoEn, actualizadoEn, borrado)
    }

    fun renombrada(nuevoNombre: String, ahora: Reloj = ::relojDelSistema) =
        copy(nombre = nuevoNombre, actualizadoEn = ahora())

    fun marcadaComoBorrada(ahora: Reloj = ::relojDelSistema) =
        copy(borrado = true, actualizadoEn = ahora())
}

fun nuevaEtiquetaDefinida(
    nombre: String,
    ahora: Reloj = ::relojDelSistema,
    generarId: GeneradorId = ::generarIdUnico,
): EtiquetaDefinida {
    val instante = ahora()
    return EtiquetaDefinida(
        id = generarId(),
        nombre = nombre,
        creadoEn = instante,
        actualizadoEn = instante,
    )
}
