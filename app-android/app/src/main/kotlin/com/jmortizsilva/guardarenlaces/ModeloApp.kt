package com.jmortizsilva.guardarenlaces

import com.jmortizsilva.guardarenlaces.dominio.AsentarCuenta
import com.jmortizsilva.guardarenlaces.dominio.AsientoDeCuenta
import com.jmortizsilva.guardarenlaces.dominio.Biblioteca
import com.jmortizsilva.guardarenlaces.dominio.Duplicados
import com.jmortizsilva.guardarenlaces.dominio.Elemento
import com.jmortizsilva.guardarenlaces.dominio.EnlacesEnElTelefono
import com.jmortizsilva.guardarenlaces.dominio.Importar
import com.jmortizsilva.guardarenlaces.dominio.Login
import com.jmortizsilva.guardarenlaces.dominio.MarcaDeTiempo
import com.jmortizsilva.guardarenlaces.dominio.MetadatosExtraidos
import com.jmortizsilva.guardarenlaces.dominio.PasoAlEntrar
import com.jmortizsilva.guardarenlaces.dominio.Proveedor
import com.jmortizsilva.guardarenlaces.dominio.Sincronizacion
import com.jmortizsilva.guardarenlaces.dominio.Textos
import com.jmortizsilva.guardarenlaces.dominio.relojDelSistema
import com.jmortizsilva.guardarenlaces.fontaneria.AlmacenLocal
import com.jmortizsilva.guardarenlaces.fontaneria.ClienteApi
import com.jmortizsilva.guardarenlaces.fontaneria.CrearEtiqueta
import com.jmortizsilva.guardarenlaces.fontaneria.ErrorApi
import com.jmortizsilva.guardarenlaces.fontaneria.GuardarEnlace
import com.jmortizsilva.guardarenlaces.fontaneria.ResolverMetadatos
import com.jmortizsilva.guardarenlaces.fontaneria.Sesion
import com.jmortizsilva.guardarenlaces.fontaneria.Sincronizador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lo que ven y piden las pantallas: los enlaces, las etiquetas y los cambios sobre ellos.
 *
 * El almacén se lee en el hilo principal, como en iOS: SQLite local tarda microsegundos, y volver
 * asíncrona cada lectura complicaría todo sin ganar nada. La red, en cambio, nunca bloquea: la
 * sincronización va en una corrutina.
 */
class ModeloApp(
    private val almacen: AlmacenLocal,
    private val cliente: ClienteApi,
    private val sesion: Sesion,
    private val sincronizador: Sincronizador,
    private val resolvedor: ResolverMetadatos,
    val anuncios: Anuncios,
    private val alcance: CoroutineScope = MainScope(),
) {
    private val _elementos = MutableStateFlow<List<Elemento>>(emptyList())

    /** Los enlaces no borrados, más recientes primero. */
    val elementos: StateFlow<List<Elemento>> = _elementos.asStateFlow()

    private val _etiquetasDisponibles = MutableStateFlow<List<String>>(emptyList())

    /** Las que lleva algún enlace más las reservadas. */
    val etiquetasDisponibles: StateFlow<List<String>> = _etiquetasDisponibles.asStateFlow()

    val conCuenta: Boolean
        get() = sesion.conCuenta

    /** Con qué cuenta se está, para que Ajustes cambie en cuanto se entra o se sale. */
    sealed interface EstadoCuenta {
        data object SinCuenta : EstadoCuenta

        /**
         * `email` es nulo mientras no se ha hablado con el servidor desde que se abrió la app, por
         * ejemplo al arrancar sin red: la cuenta está, pero todavía no se sabe de quién es.
         */
        data class ConCuenta(val email: String?) : EstadoCuenta
    }

    private val _cuenta = MutableStateFlow(estadoCuenta())
    val cuenta: StateFlow<EstadoCuenta> = _cuenta.asStateFlow()

    private fun estadoCuenta(): EstadoCuenta =
        if (sesion.conCuenta) EstadoCuenta.ConCuenta(sesion.usuario?.email)
        else EstadoCuenta.SinCuenta

    private fun avisarDeLaCuenta() {
        _cuenta.value = estadoCuenta()
    }

    /** Para no sincronizar dos veces seguidas al alternar entre aplicaciones. */
    @Volatile private var ultimaSincronizacion: MarcaDeTiempo = 0

    init {
        refrescar()
    }

    fun refrescar() {
        val visibles = Sincronizacion.elementosVisibles(almacen.cargarTodos())
        val reservadas =
            Sincronizacion.etiquetasReservadasVisibles(almacen.cargarEtiquetasDefinidas()).map {
                it.nombre
            }
        _elementos.value = visibles
        _etiquetasDisponibles.value = Biblioteca.etiquetasDisponibles(visibles, reservadas)
    }

    /**
     * Deja la lápida y la encola. No anuncia nada: quien llama mueve antes el foco a la fila
     * siguiente, y el anuncio va después. Al revés, el cambio de foco cortaría el anuncio a medias
     * (lo documenta la app de Expo, `foco.ts`).
     */
    fun eliminar(elemento: Elemento) {
        almacen.marcarPendiente(elemento.marcadoComoBorrado())
        refrescar()
        sincronizarEnSilencio()
    }

    /** Como `eliminar`, sin anunciar: quien llama lleva antes el cursor adonde toca. */
    fun cambiarEtiquetas(elemento: Elemento, etiquetas: List<String>) {
        almacen.marcarPendiente(elemento.conEtiquetas(etiquetas))
        refrescar()
        sincronizarEnSilencio()
    }

    /** El enlace ya guardado con esa URL, si lo hay: para avisar antes de guardar, no después. */
    fun repetido(url: String): Elemento? = Duplicados.buscar(elementos.value, url)

    /** Título y descripción de una URL. Nunca lanza: guardar no depende de esto. */
    suspend fun comprobar(url: String): MetadatosExtraidos? = resolvedor.resolver(url)

    /**
     * Guarda la URL, o actualiza el enlace que ya la tenía. No anuncia: quien llama lleva antes el
     * cursor al enlace, y el anuncio sale de `Resultado.anuncio`.
     */
    fun guardarEnlace(
        url: String,
        etiquetas: List<String>,
        metadatos: MetadatosExtraidos?,
    ): GuardarEnlace.Resultado =
        GuardarEnlace.guardar(url, etiquetas, metadatos, almacen).also {
            refrescar()
            sincronizarEnSilencio()
        }

    /**
     * Trae los enlaces de un fichero exportado y devuelve lo que hay que contar. El comportamiento
     * lo manda `IMPORTAR.md`, igual para los tres clientes; aquí solo se guarda.
     *
     * No anuncia: el resultado se enseña en un cuadro que TalkBack lee al abrirse, y anunciarlo
     * también lo haría sonar dos veces.
     */
    fun importar(datos: ByteArray): String {
        val lectura = Importar.leer(Importar.texto(datos))
        val preparado = Importar.preparar(lectura, elementos.value)
        try {
            // Todo o nada: si falla a mitad no entra ninguno, y lo que se cuenta sigue siendo
            // verdad.
            almacen.marcarPendientes(preparado.nuevos)
        } catch (_: Exception) {
            return Textos.noSePudieronGuardar
        }
        if (preparado.nuevos.isNotEmpty()) {
            refrescar()
            sincronizarEnSilencio()
        }
        return Textos.resultadoImportacion(preparado.importados, preparado.yaEstaban)
    }

    /**
     * Las etiquetas con cuántos enlaces lleva cada una. Incluye las reservadas, que existen aunque
     * todavía no las lleve ninguno.
     */
    fun etiquetasConRecuento(): List<Pair<String, Int>> {
        val recuento = Biblioteca.recuentoPorEtiqueta(elementos.value)
        return etiquetasDisponibles.value.map { it to (recuento[it] ?: 0) }
    }

    /**
     * Crea una etiqueta que todavía no lleva ningún enlace, para tenerla lista en los tres
     * clientes. Devuelve lo que hay que decir, o `null` si no había nada que crear (en blanco, o ya
     * existía).
     */
    fun crearEtiqueta(nombre: String): String? {
        val creada = CrearEtiqueta.crear(nombre, etiquetasDisponibles.value, almacen) ?: return null
        refrescar()
        sincronizarEnSilencio()
        return Textos.etiquetaAnadida(creada.nombre)
    }

    /**
     * Cambia el nombre en todos los enlaces que la llevan, y también en la etiqueta reservada si
     * existía. Las dos cosas, o el nombre viejo reaparecería en otro cliente. Devuelve lo que hay
     * que decir, o `null` si el nombre nuevo está en blanco o es el mismo.
     *
     * Como `eliminar`, no anuncia: quien llama lleva antes el cursor a la fila.
     */
    fun renombrarEtiqueta(vieja: String, nueva: String): String? {
        val limpio = nueva.trim()
        if (limpio.isEmpty() || limpio == vieja) return null
        val cambiados = Biblioteca.renombrarEtiqueta(elementos.value, vieja, limpio)
        val reservada = etiquetaReservada(vieja)
        // Todo junto: a medias, el nombre viejo seguiría en unos enlaces y en otros no.
        almacen.enTransaccion {
            almacen.marcarPendientes(cambiados)
            reservada?.let { almacen.marcarEtiquetaPendiente(it.renombrada(limpio)) }
        }
        refrescar()
        sincronizarEnSilencio()
        return Textos.etiquetaRenombrada(vieja, limpio, cambiados.size)
    }

    /** La quita de todos los enlaces que la llevan. No anuncia, por lo mismo que renombrar. */
    fun eliminarEtiqueta(nombre: String): String {
        val cambiados = Biblioteca.quitarEtiqueta(elementos.value, nombre)
        val reservada = etiquetaReservada(nombre)
        almacen.enTransaccion {
            almacen.marcarPendientes(cambiados)
            reservada?.let { almacen.marcarEtiquetaPendiente(it.marcadaComoBorrada()) }
        }
        refrescar()
        sincronizarEnSilencio()
        return Textos.etiquetaEliminada(nombre, cambiados.size)
    }

    private fun etiquetaReservada(nombre: String) =
        Sincronizacion.etiquetasReservadasVisibles(almacen.cargarEtiquetasDefinidas()).firstOrNull {
            it.nombre == nombre
        }

    /**
     * La automática: se calla pase lo que pase. El cambio ya está en la cola local y se reintenta
     * en la siguiente; avisar de cada fallo de red sería ruido constante.
     */
    fun sincronizarEnSilencio() {
        if (!sesion.conCuenta) return
        ultimaSincronizacion = relojDelSistema()
        alcance.launch {
            try {
                sincronizador.sincronizar()
            } catch (_: ErrorApi) {}
            refrescar()
            // La primera sincronización es la que trae de quién es la cuenta.
            avisarDeLaCuenta()
        }
    }

    /**
     * Al volver a la app, lo que se haya guardado mientras tanto: en el teléfono (desde el menú de
     * compartir, más adelante) y en el ordenador. Sin esto, con cuenta no se veía lo guardado en el
     * ordenador hasta cambiar algo aquí. Como en el iPhone, con un mínimo de tiempo entre una
     * sincronización y la siguiente.
     */
    fun alVolver(ahora: MarcaDeTiempo = relojDelSistema()) {
        refrescar()
        if (Sincronizacion.tocaSincronizar(ultimaSincronizacion, ahora)) sincronizarEnSilencio()
    }

    // --- La cuenta ---

    /**
     * Entrar con Google o con Apple. Los dos van por web: `pedirCodigo` abre la dirección en el
     * navegador y devuelve lo que traiga la vuelta.
     *
     * `decidirImportacion` solo se llama si hay algo que decidir: enlaces de otra cuenta, o
     * guardados sin cuenta. Entrar en la cuenta de siempre no pregunta nada, que es el caso normal.
     */
    suspend fun iniciarSesion(
        proveedor: Proveedor,
        pedirCodigo: suspend (String) -> Login.Resultado,
        decidirImportacion: suspend (EnlacesEnElTelefono) -> Boolean,
    ): Login.Resultado {
        val resultado = pedirCodigo(cliente.urlIniciarLogin(proveedor, Login.generarEstado()))
        if (resultado !is Login.Resultado.Exito) return resultado
        try {
            sesion.entrar(resultado.codigoDeCanje)
        } catch (fallo: ErrorApi) {
            return Login.Resultado.Error(fallo.mensaje)
        } catch (_: Exception) {
            return Login.Resultado.Error(Textos.loginFallido)
        }
        asentarLaCuenta(decidirImportacion)
        avisarDeLaCuenta()
        refrescar()
        sincronizarEnSilencio()
        return resultado
    }

    /**
     * Qué hacer con lo que ya había en el teléfono. Sin correo no se toca nada: no habría con qué
     * comparar y, ante la duda, ni se tira ni se importa nada.
     */
    private suspend fun asentarLaCuenta(
        decidirImportacion: suspend (EnlacesEnElTelefono) -> Boolean
    ) {
        val email = sesion.usuario?.email ?: return
        val dueno = AsentarCuenta.identidadDueno(Configuracion.URL_API, email)
        when (
            val paso =
                AsentarCuenta.alEntrar(almacen.duenoActual(), dueno, almacen.contarElementos())
        ) {
            PasoAlEntrar.NoHacerNada -> return
            is PasoAlEntrar.Asentar -> aplicar(paso.asiento)
            is PasoAlEntrar.Preguntar ->
                aplicar(AsentarCuenta.asiento(segunRespuesta = decidirImportacion(paso.enlaces)))
        }
        // El cursor era del OTRO servidor: si no se pone a cero, la primera bajada pide «lo
        // cambiado desde» una fecha que aquí no significa nada, y se salta todo lo anterior.
        almacen.fijarCursor(0)
        almacen.fijarDueno(dueno)
    }

    private fun aplicar(asiento: AsientoDeCuenta) =
        when (asiento) {
            AsientoDeCuenta.AdoptarLoQueHay -> almacen.adoptarConIdsNuevos()
            AsientoDeCuenta.EmpezarDeCero -> almacen.vaciar()
        }

    /**
     * Los enlaces se quedan en el teléfono y la app sigue funcionando sin cuenta. El dueño NO se
     * borra a propósito: si mañana entra otra cuenta, hay que saber que esto era de alguien y
     * preguntar antes de mezclarlo.
     */
    suspend fun cerrarSesion() {
        sesion.cerrar()
        avisarDeLaCuenta()
        refrescar()
    }
}
