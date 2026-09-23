package com.jmortizsilva.guardarenlaces.dominio

/**
 * Todo lo que la app dice o lee en voz alta, junto y en un solo sitio.
 *
 * Aquí, en lógica pura, para revisarlo de una vez y probarlo en la JVM. Lo que se lee en voz alta
 * es contrato: al cambiar una frase se cambia su prueba en el mismo commit.
 *
 * Sale de los textos de iOS, pero no es una copia: lo que en iOS dice «iPhone» aquí dice
 * «teléfono», y lo que no existe en Android (el modo lector, el botón de pegar del sistema) tiene
 * texto propio. Lo que TalkBack añade por su cuenta, como «botón» o «toca dos veces para», no se
 * escribe aquí.
 */
object Textos {
    // Pantalla de la lista

    const val tituloApp = "Guárdalo"
    /** «Añadir» a secas, suelto en una barra, no dice añadir qué. */
    const val anadirEnlace = "Añadir enlace"
    const val ajustes = "Ajustes"
    /** Nombre del campo de búsqueda. En iOS no hace falta porque lo pone el sistema. */
    const val buscar = "Buscar"
    const val marcadorBusqueda = "Título, URL o etiqueta"
    /**
     * Para vaciar la búsqueda. No hace falta un «Cancelar» que cierre el teclado, como en la app de
     * Expo: con TalkBack, el gesto de atrás ya lo cierra.
     */
    const val borrarBusqueda = "Borrar búsqueda"
    const val sincronizando = "Sincronizando…"
    const val todasLasEtiquetas = "Todas"

    /** El botón del filtro dice por dónde está filtrando ahora mismo. */
    fun filtroPorEtiqueta(etiqueta: String?) =
        "Filtrar por etiqueta: ${etiqueta ?: todasLasEtiquetas}"

    /**
     * Qué poner cuando no sale ningún enlace. Decir «no hay enlaces guardados» con un filtro puesto
     * es mentira, y de las que hacen dudar de si se ha perdido algo.
     */
    fun listaVacia(busqueda: String, etiqueta: String?): String {
        val texto = busqueda.trim()
        return when {
            texto.isEmpty() && etiqueta == null -> "No hay enlaces guardados todavía."
            texto.isEmpty() -> "Ningún enlace con la etiqueta «$etiqueta»."
            etiqueta == null -> "Ningún enlace con «$texto»."
            else -> "Ningún enlace con «$texto» y la etiqueta «$etiqueta»."
        }
    }

    // Acciones sobre un enlace

    /**
     * Lo que hace tocar una fila. En iOS es «Abrir en modo lector», pero Android no tiene modo
     * lector que se pueda pedir desde la app: la página se abre en una pestaña del navegador, y el
     * texto tiene que decir lo que pasa de verdad.
     */
    const val abrir = "Abrir"
    const val verDetalles = "Ver detalles"
    const val editarEtiquetas = "Editar etiquetas"
    const val copiarUrl = "Copiar URL"
    const val eliminar = "Eliminar"
    /**
     * El botón de cada fila para quien usa la pantalla mirando. TalkBack no lo encuentra, porque
     * esas mismas acciones ya las tiene en la fila, pero Voice Access sí lo usa por su nombre.
     */
    const val masOpciones = "Más opciones"
    const val cancelar = "Cancelar"
    const val guardar = "Guardar"
    /** Para salir de una pantalla. En iOS las pantallas son hojas y el botón es «Cerrar». */
    const val volver = "Volver"

    // Confirmar una eliminación

    fun preguntaEliminar(titulo: String) = "¿Eliminar «${recortado(titulo)}»?"

    /**
     * Lo único que de verdad cambia entre tener cuenta y no tenerla: con cuenta, esto se lleva el
     * enlace también del ordenador.
     */
    fun consecuenciaEliminar(conCuenta: Boolean) =
        if (conCuenta) "Se eliminará también en el ordenador."
        else "Está guardado solo en este teléfono."

    // Lo que se dice en voz alta

    /**
     * A medir en el teléfono: desde Android 13 el sistema enseña su propio aviso al copiar, y puede
     * que TalkBack ya lo lea. Si es así, esto sobra y se oiría dos veces.
     */
    const val urlCopiada = "URL copiada"

    /** Primero la acción y después el objeto. */
    fun eliminado(titulo: String) = "Eliminado, ${recortado(titulo)}"

    /**
     * Se dice cómo quedan las etiquetas, no que se han guardado: el resultado es lo que hay que
     * comprobar, y así no hay que ir a mirarlo.
     */
    fun etiquetasGuardadas(etiquetas: List<String>) =
        if (etiquetas.isEmpty()) "Sin etiquetas" else "Etiquetas: ${etiquetas.joinToString(", ")}"

    /**
     * Solo para la sincronización que se pide a mano. La automática se queda callada: el cambio ya
     * está en la cola y avisar de cada fallo de red sería ruido constante.
     */
    fun sincronizacionTerminada(enlacesNuevos: Int) =
        when (enlacesNuevos) {
            0 -> "Sincronizado, sin cambios"
            1 -> "Sincronizado, 1 enlace nuevo"
            else -> "Sincronizado, $enlacesNuevos enlaces nuevos"
        }

    /**
     * La acción la pone aquí quien llama, y la causa viene del fallo. Así el mismo fallo de red
     * sirve para varias acciones sin mentir en ninguna.
     */
    fun sincronizacionFallida(causa: String) = "No se pudo sincronizar: $causa"

    // Elegir etiquetas

    const val nuevaEtiqueta = "Nueva etiqueta"
    const val anadir = "Añadir"

    // Añadir un enlace

    const val anadirEnlaceTitulo = "Añadir enlace"
    const val campoUrl = "URL del enlace"
    const val marcadorUrl = "https://…"
    const val comprobando = "Comprobando…"
    const val actualizar = "Actualizar"
    const val urlNoValida = "Escribe una dirección que empiece por http:// o https://"

    /**
     * Se dice en cuanto se detecta, no al guardar: enterarte de que estaba repetido cuando ya lo
     * has guardado no sirve de nada.
     */
    const val enlaceRepetido =
        "Ya tienes guardado este enlace. Al guardar se actualiza, y las etiquetas nuevas se " +
            "suman a las que ya tenía."

    /** Para el botón que abre las etiquetas, que dice cuáles llevas puestas. */
    fun botonEtiquetas(etiquetas: List<String>) =
        if (etiquetas.isEmpty()) "Etiquetas: ninguna"
        else "Etiquetas: ${etiquetas.joinToString(", ")}"

    fun guardado(titulo: String) = "Guardado, ${recortado(titulo)}"

    fun actualizado(titulo: String) = "Actualizado, ${recortado(titulo)}"

    /**
     * Guardar nunca depende de que la comprobación salga bien, pero si no salió hay que decirlo: si
     * no, esa fila aparece en la lista con la dirección por título y no se sabe por qué.
     */
    const val guardadoSinComprobar = "Guardado sin título, no se pudo comprobar la página"

    /**
     * El botón de pegar solo aparece si hay algo que pegar, y un botón que aparece y desaparece no
     * lo encuentra quien no mira la pantalla: hay que contarlo, o es como si no estuviera.
     *
     * Dos frases porque Android solo sabe si lo copiado parece una dirección a partir de la API 31
     * (sin leerlo, que es lo que evita el aviso del sistema). Por debajo solo sabe que hay texto.
     */
    const val hayEnlaceCopiado = "Hay un enlace copiado, puedes pegarlo"
    const val hayTextoCopiado = "Hay texto copiado, puedes pegarlo"
    /** En iOS lo pone el sistema con su `PasteButton`; en Android el botón es de la app. */
    const val pegar = "Pegar"

    // Ajustes

    const val ajustesTitulo = "Ajustes"

    fun sesionIniciadaComo(email: String) =
        "Sesión iniciada como $email. Tus enlaces se sincronizan con el ordenador."

    const val sinCuenta = "Sin cuenta: los enlaces se guardan solo en este teléfono."
    const val entrarConGoogle = "Entrar con Google"
    const val entrarConApple = "Entrar con Apple"
    const val pistaEntrar =
        "Hace falta para tener los mismos enlaces en el teléfono y en el ordenador"
    const val cerrarSesion = "Cerrar sesión"
    const val pistaCerrarSesion =
        "Los enlaces se quedan en este teléfono y la aplicación sigue funcionando sin cuenta"

    fun version(numero: String, compilacion: String) = "Versión $numero ($compilacion)"

    // Entrar con una cuenta

    const val loginTitulo = "Entrar con una cuenta"
    const val loginExplicacion =
        "La cuenta sirve para tener los mismos enlaces en el teléfono y en el ordenador. Sin ella " +
            "la aplicación funciona igual, pero los enlaces se quedan solo en este teléfono."
    /**
     * Se avisa de que se abre el navegador: sin decirlo, aparece otra pantalla de la nada, con otro
     * aspecto y otros controles.
     */
    const val pistaLogin =
        "Se abre el navegador para confirmar tu cuenta y vuelves aquí al terminar"
    const val ahoraNo = "Ahora no"
    const val sesionIniciada = "Sesión iniciada. Tus enlaces se sincronizarán con el ordenador."
    const val loginSinCorreo =
        "Tu cuenta no ha dado ningún correo, y hace falta para crear la cuenta."

    fun loginRechazado(proveedor: String) =
        "$proveedor rechazó el inicio de sesión. Vuelve a intentarlo."

    const val loginFallido = "No se pudo iniciar sesión."

    // Qué hacer con lo que ya había en el teléfono

    const val tituloEnlacesEnElTelefono = "Enlaces en este teléfono"

    /** Se dice cuántos son y qué pasa con cada respuesta: ninguna de las dos se puede deshacer. */
    fun preguntaImportar(cuantos: Int, deOtraCuenta: Boolean): String {
        val cuenta = if (cuantos == 1) "1 enlace guardado" else "$cuantos enlaces guardados"
        val origen = if (deOtraCuenta) "con otra cuenta" else "sin cuenta"
        return "Hay $cuenta en este teléfono $origen. ¿Quieres añadirlos a esta cuenta? " +
            "Si eliges borrarlos, se quitan de este teléfono y no se pueden recuperar."
    }

    const val anadirlos = "Añadirlos"
    const val borrarlos = "Borrarlos"

    // Detalle de un enlace

    const val campoDireccion = "URL"
    const val campoDescripcion = "Descripción"
    const val campoEtiquetas = "Etiquetas"
    const val ningunaEtiqueta = "Ninguna"

    fun guardadoEl(fecha: String) = "Guardado el $fecha"

    // Gestionar las etiquetas de toda la biblioteca

    const val gestionarEtiquetas = "Gestionar etiquetas"
    const val renombrar = "Renombrar"
    const val nuevoNombre = "Nuevo nombre"
    const val crearEtiqueta = "Crear etiqueta"

    /**
     * Cuántos enlaces lleva cada etiqueta, dicho en la propia fila: sin esto hay que salir a
     * contarlos antes de decidir si renombrarla o tirarla.
     */
    fun etiquetaConRecuento(nombre: String, enlaces: Int) =
        when (enlaces) {
            0 -> "$nombre, ningún enlace"
            1 -> "$nombre, 1 enlace"
            else -> "$nombre, $enlaces enlaces"
        }

    /** Se avisa antes, porque esto toca todos los enlaces que la llevan y no se puede deshacer. */
    fun preguntaEliminarEtiqueta(nombre: String, enlaces: Int) =
        "¿Eliminar la etiqueta «$nombre»? Se quitará de ${enlacesEnTexto(enlaces)} y no se puede " +
            "deshacer."

    fun etiquetaRenombrada(de: String, a: String, enlaces: Int) =
        "Etiqueta «$de» renombrada a «$a» en ${enlacesEnTexto(enlaces)}"

    fun etiquetaEliminada(nombre: String, enlaces: Int) =
        "Etiqueta «$nombre» eliminada de ${enlacesEnTexto(enlaces)}"

    fun etiquetaAnadida(nombre: String) = "Etiqueta «$nombre» añadida"

    private fun enlacesEnTexto(enlaces: Int) = if (enlaces == 1) "1 enlace" else "$enlaces enlaces"

    // La primera vez que se abre la app

    const val bienvenidaTitulo = "Guárdalo"
    const val bienvenidaQueEs =
        "Guarda enlaces para leerlos cuando quieras, con su título y su descripción, " +
            "organizados por etiquetas."
    /** Lo que de verdad hay que decidir aquí, y en qué se nota cada respuesta. */
    const val bienvenidaCuenta =
        "Con cuenta, los mismos enlaces están en este teléfono y en el ordenador. Sin cuenta, la " +
            "aplicación funciona igual, pero los enlaces se quedan solo aquí."
    const val usarSinCuenta = "Usar sin cuenta"
    /** Para que «Usar sin cuenta» no parezca una puerta que se cierra. */
    const val bienvenidaMasTarde = "Puedes crear la cuenta más tarde desde Ajustes."

    // Compartir un enlace desde otra aplicación

    /**
     * La pantalla que sale al compartir se ve encima de otra aplicación, así que dice su nombre:
     * quien la ve acaba de tocar un icono en una lista larga. «Cancelar» y «Guardar» son los mismos
     * de la pantalla de añadir: el mismo botón no se llama distinto según por dónde se haya
     * llegado.
     */
    const val compartirTitulo = "Guárdalo"
    /**
     * El título de la página todavía no se sabe al abrirse la pantalla, y un hueco vacío deja al
     * lector de pantalla sin nada que leer.
     */
    const val compartirSinTitulo = "Sin título todavía"
    const val compartirEtiquetas = "Etiquetas"
    const val compartirSinEtiquetas = "Todavía no tienes etiquetas."
    /**
     * El menú de compartir ofrece esta aplicación para más cosas de las que sabe guardar. Cuando
     * llega un texto sin dirección hay que decirlo y no quedarse en blanco.
     */
    const val compartirNoEsEnlace = "Esto no es un enlace, y solo se guardan enlaces."
    /**
     * Si falla el guardado no se puede ofrecer «reintentar»: la pantalla se cierra y el enlace se
     * va con ella. Lo único útil es decir dónde hacerlo a mano.
     */
    const val compartirNoSePudo =
        "No se pudo guardar. Copia la dirección y añádela desde la aplicación."

    // Guardar sin abrir la aplicación

    const val guardadoSilencioso = "Guardar sin preguntar"
    const val pistaGuardadoSilencioso =
        "Al compartir un enlace se guarda al momento, sin enseñar esta pantalla"

    // Recortes

    /**
     * Un título largo leído entero después de cada acción es una tortura. Se corta por la última
     * palabra que quepa, no a mitad de palabra.
     *
     * Se cuenta por puntos de código y no por `length`, que cuenta mitades de emoji: cortar ahí
     * deja medio carácter, que el lector de pantalla lee como un símbolo raro.
     */
    fun recortado(texto: String, limite: Int = 60): String {
        if (texto.codePointCount(0, texto.length) <= limite) return texto
        val cortado = texto.substring(0, texto.offsetByCodePoints(0, limite))
        val ultimoEspacio = cortado.lastIndexOf(' ')
        return if (ultimoEspacio < 0) "$cortado…" else cortado.substring(0, ultimoEspacio) + "…"
    }
}
