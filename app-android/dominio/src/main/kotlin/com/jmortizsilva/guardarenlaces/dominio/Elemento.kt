package com.jmortizsilva.guardarenlaces.dominio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TipoElemento(val valor: String) {
    @SerialName("enlace") Enlace("enlace"),
    @SerialName("video") Video("video"),
    @SerialName("articulo") Articulo("articulo"),
    @SerialName("imagen") Imagen("imagen");

    companion object {
        /**
         * Un tipo que aquí no se conozca no invalida el elemento: el contrato puede añadir tipos y
         * esta app tiene que seguir guardando el enlace. Para leer la columna de la base de datos;
         * el JSON lo resuelve `jsonDelContrato`.
         */
        fun desde(valor: String?): TipoElemento =
            entries.firstOrNull { it.valor == valor } ?: Enlace
    }
}

/**
 * Un enlace guardado.
 *
 * El identificador lo genera el cliente (uuid v4): permite guardar sin conexión y hace que subirlo
 * dos veces no cree dos elementos. `borrado` es una lápida, no un borrado de verdad: el elemento
 * tiene que seguir existiendo para que los demás dispositivos se enteren de la baja.
 *
 * El constructor y `copy` son privados: desde fuera es inmutable, y los cambios se piden con los
 * métodos de abajo, que son los que mueven `actualizadoEn`. Olvidarse de esa fecha al cambiar algo
 * es perder el cambio en cuanto sincronice, porque gana el más reciente. En iOS esto lo hace
 * `private(set)`; en Kotlin, un `copy` público lo dejaría pasar.
 *
 * Los valores por defecto son los que se usan cuando el servidor no manda el campo. El
 * identificador no tiene: un elemento sin él no se puede guardar, ni comparar, ni volver a subir.
 */
@ConsistentCopyVisibility
@Serializable
data class Elemento
private constructor(
    val id: String,
    val url: String = "",
    val titulo: String? = null,
    val descripcion: String? = null,
    val imagenUrl: String? = null,
    val tipo: TipoElemento = TipoElemento.Enlace,
    val etiquetas: List<String> = emptyList(),
    val creadoEn: MarcaDeTiempo = 0,
    val actualizadoEn: MarcaDeTiempo = 0,
    val borrado: Boolean = false,
) {
    companion object {
        /** Para lo que ya existe (lo que sale de la base de datos) y para las pruebas. */
        operator fun invoke(
            id: String,
            url: String,
            titulo: String? = null,
            descripcion: String? = null,
            imagenUrl: String? = null,
            tipo: TipoElemento = TipoElemento.Enlace,
            etiquetas: List<String> = emptyList(),
            creadoEn: MarcaDeTiempo = 0,
            actualizadoEn: MarcaDeTiempo = 0,
            borrado: Boolean = false,
        ) =
            Elemento(
                id,
                url,
                titulo,
                descripcion,
                imagenUrl,
                tipo,
                etiquetas,
                creadoEn,
                actualizadoEn,
                borrado,
            )
    }

    /**
     * Baja lógica: el servidor necesita ver el borrado para avisar a los demás dispositivos, así
     * que la fila nunca se tira de golpe.
     */
    fun marcadoComoBorrado(ahora: Reloj = ::relojDelSistema) =
        copy(borrado = true, actualizadoEn = ahora())

    fun conEtiquetas(nuevas: List<String>, ahora: Reloj = ::relojDelSistema) =
        copy(etiquetas = nuevas, actualizadoEn = ahora())

    fun conMetadatos(
        titulo: String?,
        descripcion: String?,
        imagenUrl: String?,
        tipo: TipoElemento,
        ahora: Reloj = ::relojDelSistema,
    ) =
        copy(
            titulo = titulo,
            descripcion = descripcion,
            imagenUrl = imagenUrl,
            tipo = tipo,
            actualizadoEn = ahora(),
        )

    /**
     * Un enlace que ya estaba guardado, refrescado con lo que diga la comprobación de ahora y con
     * las etiquetas nuevas sumadas a las suyas.
     *
     * Es lo que pasa al guardar una URL que ya tienes: no se crea otro enlace, se actualiza este.
     * Dos detalles que parecen pequeños y no lo son: si la comprobación no trajo título (sin red,
     * sitio caído), se conserva el que ya había en vez de vaciarlo; y las etiquetas se suman,
     * porque quitar en silencio una etiqueta que pusiste hace un mes sería peor que no guardar
     * nada.
     */
    fun actualizado(
        con: MetadatosExtraidos?,
        etiquetasNuevas: List<String> = emptyList(),
        ahora: Reloj = ::relojDelSistema,
    ) =
        copy(
            titulo = con?.titulo ?: titulo,
            descripcion = con?.descripcion ?: descripcion,
            imagenUrl = con?.imagenUrl ?: imagenUrl,
            tipo = con?.tipo ?: tipo,
            etiquetas = (etiquetas + etiquetasNuevas).distinct(),
            actualizadoEn = ahora(),
        )
}

data class DatosElementoNuevo(
    val url: String,
    val titulo: String? = null,
    val descripcion: String? = null,
    val imagenUrl: String? = null,
    val tipo: TipoElemento = TipoElemento.Enlace,
    val etiquetas: List<String> = emptyList(),
)

fun nuevoElementoLocal(
    datos: DatosElementoNuevo,
    ahora: Reloj = ::relojDelSistema,
    generarId: GeneradorId = ::generarIdUnico,
): Elemento {
    val instante = ahora()
    return Elemento(
        id = generarId(),
        url = datos.url,
        titulo = datos.titulo,
        descripcion = datos.descripcion,
        imagenUrl = datos.imagenUrl,
        tipo = datos.tipo,
        etiquetas = datos.etiquetas,
        creadoEn = instante,
        actualizadoEn = instante,
    )
}
