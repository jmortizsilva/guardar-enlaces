package com.jmortizsilva.guardarenlaces

import android.app.Application
import android.content.Context
import com.jmortizsilva.guardarenlaces.fontaneria.ClienteApi
import com.jmortizsilva.guardarenlaces.fontaneria.ResolverMetadatos
import com.jmortizsilva.guardarenlaces.fontaneria.Sesion
import com.jmortizsilva.guardarenlaces.fontaneria.Sincronizador

/**
 * La aplicación. Tiene el contenedor para que la pantalla principal y, más adelante, la de
 * compartir usen el mismo almacén y la misma sesión: en Android las dos viven en el mismo proceso.
 */
class Guardalo : Application() {
    val contenedor by lazy { Contenedor(this) }
}

/** A qué servidor habla la app. El mismo que iOS y Windows. */
object Configuracion {
    const val URL_API = "https://api.jmortiz.es"
}

/** Cablea almacén, cliente, sesión y sincronizador. El equivalente de `ModeloApp.init` en iOS. */
class Contenedor(contexto: Context) {
    val almacen = Ubicaciones.abrirAlmacen(contexto)
    val cliente = ClienteApi(Configuracion.URL_API)
    val sesion = Sesion(cliente, CredencialesKeystore(Ubicaciones.ficheroCredenciales(contexto)))
    val sincronizador = Sincronizador(almacen, cliente, sesion)
    val resolvedor = ResolverMetadatos(cliente, sesion)
    val anuncios = Anuncios()
    val modelo = ModeloApp(almacen, sesion, sincronizador, resolvedor, anuncios)
}
