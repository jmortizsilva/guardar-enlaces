package com.jmortizsilva.guardarenlaces.fontaneria

import com.jmortizsilva.guardarenlaces.dominio.DatosElementoNuevo
import com.jmortizsilva.guardarenlaces.dominio.Duplicados
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.EtiquetaDefinida
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.Presentacion
import com.jmortizsilva.guardarenlaces.dominio.Reloj
import com.jmortizsilva.guardarenlaces.dominio.Sincronizacion
import com.jmortizsilva.guardarenlaces.dominio.Textos
import com.jmortizsilva.guardarenlaces.dominio.TipoElemento
import com.jmortizsilva.guardarenlaces.dominio.nuevaEtiquetaDefinida
import com.jmortizsilva.guardarenlaces.dominio.nuevoElementoLocal
import com.jmortizsilva.guardarenlaces.dominio.relojDelSistema

/**
 * Guardar un enlace, que es lo único que hacen las dos: la pantalla de añadir y la de compartir
 * desde otra aplicación.
 *
 * Está aquí y no en cada una porque el comportamiento con un enlace repetido (actualizar el que
 * había, sumar las etiquetas, conservar el título si la comprobación no trajo ninguno) es justo lo
 * que costó acordar entre los clientes, y con dos copias volvería a separarse sin que nadie lo
 * notara hasta usarlas por separado.
 */
object GuardarEnlace {
    sealed interface Resultado {
        val elemento: Elemento

        data class Nuevo(override val elemento: Elemento) : Resultado

        data class Actualizado(override val elemento: Elemento) : Resultado

        /**
         * Lo que se dice en voz alta. Sale de aquí y no de cada pantalla para que compartir desde
         * otra aplicación y guardar desde esta suenen igual.
         */
        fun anuncio(seComprobo: Boolean): String =
            when (this) {
                is Nuevo ->
                    if (seComprobo) Textos.guardado(Presentacion.titulo(elemento))
                    else Textos.guardadoSinComprobar
                is Actualizado -> Textos.actualizado(Presentacion.titulo(elemento))
            }
    }

    /**
     * Deja el enlace guardado y encolado para subir.
     *
     * `metadatos` puede venir vacío y no pasa nada: se guarda igual, sin título. Comprobar la
     * página nunca fue lo importante.
     */
    fun guardar(
        url: String,
        etiquetas: List<String>,
        metadatos: MetadatosExtraidos?,
        almacen: AlmacenLocal,
        ahora: Reloj = ::relojDelSistema,
    ): Resultado = almacen.enTransaccion {
        val guardados = Sincronizacion.elementosVisibles(almacen.cargarTodos())
        val repetido = Duplicados.buscar(guardados, url)
        val resultado =
            if (repetido != null) {
                Resultado.Actualizado(repetido.actualizado(metadatos, etiquetas, ahora))
            } else {
                Resultado.Nuevo(
                    nuevoElementoLocal(
                        DatosElementoNuevo(
                            url = url,
                            titulo = metadatos?.titulo,
                            descripcion = metadatos?.descripcion,
                            imagenUrl = metadatos?.imagenUrl,
                            tipo = metadatos?.tipo ?: TipoElemento.Enlace,
                            etiquetas = etiquetas,
                        ),
                        ahora,
                    )
                )
            }
        almacen.marcarPendiente(resultado.elemento)
        resultado
    }
}

/**
 * Crear una etiqueta que todavía no lleva ningún enlace. Igual que `GuardarEnlace`, esto lo hacen
 * la pantalla de añadir y la de compartir, y tiene que ser lo mismo.
 */
object CrearEtiqueta {
    /**
     * Devuelve la etiqueta creada, o `null` si no había nada que crear: el nombre en blanco, o una
     * que ya existía. Las dos cosas son ruido, no errores, y quien llama no tiene que
     * distinguirlas.
     */
    fun crear(
        nombre: String,
        existentes: List<String>,
        almacen: AlmacenLocal,
        ahora: Reloj = ::relojDelSistema,
    ): EtiquetaDefinida? {
        val limpio = nombre.trim()
        if (limpio.isEmpty() || limpio in existentes) return null
        return nuevaEtiquetaDefinida(limpio, ahora).also(almacen::marcarEtiquetaPendiente)
    }
}
