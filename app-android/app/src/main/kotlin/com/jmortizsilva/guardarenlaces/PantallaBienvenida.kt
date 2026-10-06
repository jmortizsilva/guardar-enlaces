package com.jmortizsilva.guardarenlaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmortizsilva.guardarenlaces.dominio.Login
import com.jmortizsilva.guardarenlaces.dominio.Proveedor
import com.jmortizsilva.guardarenlaces.dominio.Textos

/**
 * Lo primero que se ve al estrenar la app: qué es, y qué cambia según se entre con cuenta o no.
 *
 * Las tres opciones están al mismo nivel a propósito: usar la app sin cuenta no es una salida de
 * emergencia, es una forma de usarla. Google va antes que Apple, al revés que en el iPhone: allí
 * Apple entra sin salir de la app, aquí las dos van por el navegador, y es el orden de Ajustes.
 *
 * El gesto de atrás no se toca: sale de la app sin dar la bienvenida por vista, y vuelve a salir la
 * próxima vez. Que valiera como «Usar sin cuenta» decidiría algo que no se ha elegido.
 *
 * `alTerminar` recibe lo que hay que decir al llegar a la lista, o nulo.
 */
@Composable
fun PantallaBienvenida(anuncios: Anuncios, entrar: Entrar, alTerminar: (String?) -> Unit) {
    val entrada = rememberEntradaConCuenta(entrar)
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun entrarCon(proveedor: Proveedor) {
        entrada.entrarCon(proveedor) { salida ->
            when (salida) {
                is Login.Resultado.Exito -> alTerminar(Textos.sesionIniciada)
                is Login.Resultado.Error -> error = salida.mensaje
                // Lo ha hecho quien usa la app: se queda aquí para elegir otra cosa.
                Login.Resultado.Cancelado -> Unit
            }
        }
    }

    CursorAlTituloAlEntrar()
    Scaffold(
        topBar = { BarraSuperior(Textos.bienvenidaTitulo) },
        bottomBar = { LineaDeAvisos(anuncios) },
    ) { margen ->
        Column(Modifier.padding(margen).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(Textos.bienvenidaQueEs, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Text(Textos.bienvenidaCuenta, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Opcion(Textos.entrarConGoogle, Textos.pistaLogin, activa = !entrada.ocupada) {
                entrarCon(Proveedor.Google)
            }
            Opcion(Textos.entrarConApple, Textos.pistaLogin, activa = !entrada.ocupada) {
                entrarCon(Proveedor.Apple)
            }
            Opcion(Textos.usarSinCuenta, Textos.bienvenidaMasTarde, activa = !entrada.ocupada) {
                alTerminar(null)
            }
        }
    }

    PreguntaEnlacesEnElTelefono(entrada)
    error?.let { CuadroResultado(it, alAceptar = { error = null }) }
}
