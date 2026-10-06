package com.jmortizsilva.guardarenlaces.dominio

/** Si una página responde. Ver «Comprobar que carga» en `ANADIR.md`. */
sealed interface Comprobacion {
    /** Responde. Los metadatos pueden venir vacíos: una página sin título también carga. */
    data class Carga(val metadatos: MetadatosExtraidos?) : Comprobacion

    /** Hay conexión, pero la página no responde o responde con un error. */
    data object NoCarga : Comprobacion

    /** No se ha podido preguntar: sin red, o el servidor no contesta. Se guarda sin preguntar. */
    data object SinComprobar : Comprobacion
}

/** La dirección que se va a guardar, y lo que se sabe de ella. */
data class DireccionComprobada(val direccion: String, val comprobacion: Comprobacion)

object ComprobarDireccion {
    /**
     * Prueba la dirección y, si no carga y el `https://` lo puso la app, la alternativa con
     * `http://`: algunas páginas antiguas solo responden así. Se queda con la que cargue; si no
     * carga ninguna, con la de `https://`, que es la que se ofrecerá guardar igualmente.
     */
    suspend fun comprobar(
        escrita: DireccionEscrita,
        comprobar: suspend (String) -> Comprobacion,
    ): DireccionComprobada {
        val primera = comprobar(escrita.direccion)
        val alternativa = escrita.alternativa
        if (primera != Comprobacion.NoCarga || alternativa == null) {
            return DireccionComprobada(escrita.direccion, primera)
        }
        val segunda = comprobar(alternativa)
        return if (segunda is Comprobacion.Carga) DireccionComprobada(alternativa, segunda)
        else DireccionComprobada(escrita.direccion, primera)
    }
}
