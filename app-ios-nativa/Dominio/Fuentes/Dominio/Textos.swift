import Foundation

/// Todo lo que la app dice o lee en voz alta, junto y en un solo sitio.
///
/// Está aquí, en lógica pura, por dos motivos: se revisa de una vez en vez de
/// ir persiguiéndolo por las pantallas, y se prueba sin simulador. Lo que se
/// lee en voz alta es contrato: al cambiar una frase se cambia su prueba en
/// el mismo commit, o la prueba deja de proteger nada.
public enum Textos {
    // MARK: - Pantalla de la lista

    public static let tituloApp = "Guárdalo"
    /// «Añadir» a secas, suelto en una barra, no dice añadir qué.
    public static let anadirEnlace = "Añadir enlace"
    public static let ajustes = "Ajustes"
    public static let marcadorBusqueda = "Título, URL o etiqueta"
    public static let sincronizando = "Sincronizando…"
    public static let todasLasEtiquetas = "Todas"

    /// El botón del filtro dice por dónde está filtrando ahora mismo. Sin
    /// pista añadida: el papel de botón ya cuenta que se puede pulsar.
    public static func filtroPorEtiqueta(_ etiqueta: String?) -> String {
        "Filtrar por etiqueta: \(etiqueta ?? todasLasEtiquetas)"
    }

    /// Qué poner cuando no sale ningún enlace. Decir «no hay enlaces
    /// guardados» con un filtro puesto es mentira, y de las que hacen dudar
    /// de si se ha perdido algo.
    public static func listaVacia(busqueda: String, etiqueta: String?) -> String {
        let texto = busqueda.trimmingCharacters(in: .whitespacesAndNewlines)
        switch (texto.isEmpty, etiqueta) {
        case (true, nil):
            return "No hay enlaces guardados todavía."
        case (true, let etiqueta?):
            return "Ningún enlace con la etiqueta «\(etiqueta)»."
        case (false, nil):
            return "Ningún enlace con «\(texto)»."
        case (false, let etiqueta?):
            return "Ningún enlace con «\(texto)» y la etiqueta «\(etiqueta)»."
        }
    }

    // MARK: - Acciones sobre un enlace

    public static let abrirEnModoLector = "Abrir en modo lector"
    public static let copiarUrl = "Copiar URL"
    public static let editarEtiquetas = "Editar etiquetas"
    public static let eliminar = "Eliminar"
    public static let cancelar = "Cancelar"
    public static let guardar = "Guardar"

    // MARK: - Confirmar una eliminación

    public static func preguntaEliminar(titulo: String) -> String {
        "¿Eliminar «\(recortado(titulo))»?"
    }

    /// Lo único que de verdad cambia entre tener cuenta y no tenerla: con
    /// cuenta, esto se lleva el enlace también del PC.
    public static func consecuenciaEliminar(conCuenta: Bool) -> String {
        conCuenta
            ? "Se eliminará también en el PC."
            : "Está guardado solo en este iPhone."
    }

    // MARK: - Lo que se dice en voz alta

    public static let urlCopiada = "URL copiada"

    /// Primero la acción y después el objeto. «Elemento» era como se llama en
    /// la base de datos, no lo que tiene guardado quien lo oye.
    public static func eliminado(titulo: String) -> String {
        "Eliminado, \(recortado(titulo))"
    }

    /// Se dice cómo quedan las etiquetas, no que se han guardado: el
    /// resultado es lo que hay que comprobar, y así no hay que ir a mirarlo.
    public static func etiquetasGuardadas(_ etiquetas: [String]) -> String {
        etiquetas.isEmpty ? "Sin etiquetas" : "Etiquetas: \(etiquetas.joined(separator: ", "))"
    }

    /// Solo para la sincronización que se pide a mano. La automática se queda
    /// callada: el cambio ya está en la cola y avisar de cada fallo de red
    /// sería ruido constante.
    public static func sincronizacionTerminada(enlacesNuevos: Int) -> String {
        switch enlacesNuevos {
        case 0: "Sincronizado, sin cambios"
        case 1: "Sincronizado, 1 enlace nuevo"
        default: "Sincronizado, \(enlacesNuevos) enlaces nuevos"
        }
    }

    /// La acción la pone aquí quien llama, y la causa viene del fallo. Así el
    /// mismo fallo de red sirve para varias acciones sin mentir en ninguna.
    public static func sincronizacionFallida(causa: String) -> String {
        "No se pudo sincronizar: \(causa)"
    }

    // MARK: - Recortes

    /// Un título largo leído entero después de cada acción es una tortura.
    /// Se corta por la última palabra que quepa, no a mitad de palabra.
    static func recortado(_ texto: String, a limite: Int = 60) -> String {
        guard texto.count > limite else {
            return texto
        }
        let cortado = texto.prefix(limite)
        guard let ultimoEspacio = cortado.lastIndex(of: " ") else {
            return cortado + "…"
        }
        return cortado[..<ultimoEspacio] + "…"
    }
}

extension Textos {
    // MARK: - Pantalla de etiquetas

    public static let nuevaEtiqueta = "Nueva etiqueta"
    public static let anadir = "Añadir"
}

extension Textos {
    // MARK: - Pantalla de añadir un enlace

    public static let anadirEnlaceTitulo = "Añadir enlace"
    public static let campoUrl = "URL del enlace"
    public static let marcadorUrl = "https://…"
    public static let comprobando = "Comprobando…"
    public static let actualizar = "Actualizar"

    public static let urlNoValida = "Escribe una dirección que empiece por http:// o https://"

    /// Se dice en cuanto se detecta, no al guardar: enterarte de que estaba
    /// repetido cuando ya lo has guardado no sirve de nada.
    public static let enlaceRepetido =
        "Ya tienes guardado este enlace. Al guardar se actualiza, y las etiquetas nuevas se "
        + "suman a las que ya tenía."

    /// Para el botón que abre las etiquetas, que dice cuáles llevas puestas.
    public static func botonEtiquetas(_ etiquetas: [String]) -> String {
        etiquetas.isEmpty
            ? "Etiquetas: ninguna"
            : "Etiquetas: \(etiquetas.joined(separator: ", "))"
    }

    public static func guardado(titulo: String) -> String {
        "Guardado, \(recortado(titulo))"
    }

    public static func actualizado(titulo: String) -> String {
        "Actualizado, \(recortado(titulo))"
    }

    /// Guardar nunca depende de que la comprobación salga bien, pero si no
    /// salió hay que decirlo: si no, esa fila aparece en la lista con la
    /// dirección por título y no se sabe por qué.
    public static let guardadoSinComprobar =
        "Guardado sin título, no se pudo comprobar la página"
}

extension Textos {
    // MARK: - Ajustes

    public static let ajustesTitulo = "Ajustes"
    public static let cerrar = "Cerrar"

    public static func sesionIniciadaComo(email: String) -> String {
        "Sesión iniciada como \(email). Tus enlaces se sincronizan con el PC."
    }

    public static let sinCuenta = "Sin cuenta: los enlaces se guardan solo en este iPhone."
    public static let entrarConGoogle = "Entrar con Google"
    public static let pistaEntrar =
        "Hace falta para tener los mismos enlaces en el iPhone y en el PC"
    public static let cerrarSesion = "Cerrar sesión"
    public static let pistaCerrarSesion =
        "Los enlaces se quedan en este iPhone y la aplicación sigue funcionando sin cuenta"

    public static func version(_ numero: String, compilacion: String) -> String {
        "Versión \(numero) (\(compilacion))"
    }

    // MARK: - Entrar con una cuenta

    public static let loginTitulo = "Entrar con una cuenta"
    public static let loginExplicacion =
        "La cuenta sirve para tener los mismos enlaces en el iPhone y en el PC. Sin ella la "
        + "aplicación funciona igual, pero los enlaces se quedan solo en este iPhone."
    /// Se avisa de que se abre el navegador porque iOS pregunta antes si se
    /// permite usar google.com para iniciar sesión, y esa pregunta sale de la
    /// nada si no se ha dicho.
    public static let pistaLogin =
        "Se abre Safari para confirmar tu cuenta y vuelves aquí al terminar"
    public static let ahoraNo = "Ahora no"
    public static let sesionIniciada = "Sesión iniciada. Tus enlaces se sincronizarán con el PC."

    // MARK: - Qué hacer con lo que ya había en el teléfono

    public static let tituloEnlacesEnElTelefono = "Enlaces en este iPhone"

    /// Se dice cuántos son y qué pasa con cada respuesta: ninguna de las dos
    /// se puede deshacer.
    public static func preguntaImportar(cuantos: Int, deOtraCuenta: Bool) -> String {
        let cuenta = cuantos == 1 ? "1 enlace guardado" : "\(cuantos) enlaces guardados"
        let origen = deOtraCuenta ? "con otra cuenta" : "sin cuenta"
        return "Hay \(cuenta) en este iPhone \(origen). ¿Quieres añadirlos a esta cuenta? "
            + "Si eliges borrarlos, se quitan de este iPhone y no se pueden recuperar."
    }

    public static let anadirlos = "Añadirlos"
    public static let borrarlos = "Borrarlos"
}

extension Textos {
    // MARK: - Detalle de un enlace

    public static let verDetalles = "Ver detalles"
    public static let campoDireccion = "URL"
    public static let campoDescripcion = "Descripción"
    public static let campoEtiquetas = "Etiquetas"
    public static let ningunaEtiqueta = "Ninguna"

    public static func guardadoEl(_ fecha: String) -> String {
        "Guardado el \(fecha)"
    }
}

extension Textos {
    // MARK: - Gestionar las etiquetas de toda la biblioteca

    public static let gestionarEtiquetas = "Gestionar etiquetas"
    public static let renombrar = "Renombrar"
    public static let nuevoNombre = "Nuevo nombre"
    public static let crearEtiqueta = "Crear etiqueta"

    /// Cuántos enlaces lleva cada etiqueta, dicho en la propia fila: sin esto
    /// hay que salir a contarlos antes de decidir si renombrarla o tirarla.
    public static func etiquetaConRecuento(_ nombre: String, enlaces: Int) -> String {
        switch enlaces {
        case 0: "\(nombre), ningún enlace"
        case 1: "\(nombre), 1 enlace"
        default: "\(nombre), \(enlaces) enlaces"
        }
    }

    /// Se avisa antes, porque esto toca todos los enlaces que la llevan y no
    /// se puede deshacer.
    public static func preguntaEliminarEtiqueta(_ nombre: String, enlaces: Int) -> String {
        let cuantos = enlaces == 1 ? "1 enlace" : "\(enlaces) enlaces"
        return "¿Eliminar la etiqueta «\(nombre)»? Se quitará de \(cuantos) y no se puede deshacer."
    }

    public static func etiquetaRenombrada(de vieja: String, a nueva: String, enlaces: Int) -> String
    {
        "Etiqueta «\(vieja)» renombrada a «\(nueva)» en \(enlaces == 1 ? "1 enlace" : "\(enlaces) enlaces")"
    }

    public static func etiquetaEliminada(_ nombre: String, enlaces: Int) -> String {
        "Etiqueta «\(nombre)» eliminada de \(enlaces == 1 ? "1 enlace" : "\(enlaces) enlaces")"
    }

    public static func etiquetaAnadida(_ nombre: String) -> String {
        "Etiqueta «\(nombre)» añadida"
    }
}
