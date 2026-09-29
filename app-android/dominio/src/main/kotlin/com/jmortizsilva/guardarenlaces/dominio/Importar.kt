package com.jmortizsilva.guardarenlaces.dominio

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Leer enlaces de un fichero exportado por otra aplicación.
 *
 * El comportamiento no se decide aquí: manda `IMPORTAR.md`, en la raíz del repositorio, y vale para
 * los tres clientes. Las pruebas leen los mismos ficheros de ejemplo que las de Windows y el iPhone
 * (`pruebas-compartidas/importacion/`) y comparan contra el mismo `esperado.json`. Si esto se
 * desvía, su prueba falla.
 */
object Importar {
    enum class Formato {
        Html,
        Csv,
        Txt,
    }

    /** `creadoEn` nulo es «el fichero no traía fecha»: la pone quien guarde, con su reloj. */
    data class EnlaceImportado(
        val url: String,
        val titulo: String? = null,
        val etiquetas: List<String> = emptyList(),
        val creadoEn: MarcaDeTiempo? = null,
    )

    data class Lectura(
        val enlaces: List<EnlaceImportado>,
        val descartados: Int,
        val formato: Formato,
    )

    data class Preparado(val nuevos: List<Elemento>, val yaEstaban: Int, val descartados: Int) {
        val importados: Int
            get() = nuevos.size
    }

    // --- Leer ---

    /**
     * El contenido del fichero, en la codificación que traiga.
     *
     * Quien exporta no la elige ni suele saber cuál es. Los navegadores escriben UTF-8, pero Excel
     * guarda los CSV en la del sistema, y en un Windows en español eso es cp1252. Latin-1 va al
     * final porque acepta cualquier byte y nunca falla.
     */
    fun texto(de: ByteArray): String {
        for (nombre in listOf("UTF-8", "windows-1252")) {
            val codificacion = runCatching { Charset.forName(nombre) }.getOrNull() ?: continue
            val decodificador =
                codificacion
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                // La marca de orden de bytes de Excel llega como un carácter más, y pegada a
                // la cabecera de un CSV hace que no se reconozca.
                return decodificador.decode(ByteBuffer.wrap(de)).toString().removePrefix("﻿")
            } catch (_: CharacterCodingException) {}
        }
        return String(de, Charsets.ISO_8859_1)
    }

    /**
     * Por el contenido y no por la extensión: quien exporta no siempre la conserva, y un fichero de
     * marcadores llamado .txt sigue siendo HTML.
     */
    fun detectarFormato(contenido: String): Formato {
        val principio = contenido.trimStart().take(400).lowercase()
        if (
            "netscape-bookmark-file" in principio || "<dl>" in principio || "<a href" in principio
        ) {
            return Formato.Html
        }
        val primera = primeraLineaConAlgo(contenido)
        if (primera != null && separador(primera) != null) return Formato.Csv
        return Formato.Txt
    }

    fun leer(contenido: String): Lectura {
        val formato = detectarFormato(contenido)
        val (crudos, descartados) =
            when (formato) {
                Formato.Html -> leerHtml(contenido)
                Formato.Csv -> leerCsv(contenido)
                Formato.Txt -> leerTxt(contenido)
            }
        val (enlaces, repetidos) = quitarRepetidos(crudos)
        return Lectura(enlaces, descartados + repetidos, formato)
    }

    // --- Qué se guarda de lo leído ---

    /**
     * Decide qué enlaces hay que guardar. Recibe lo que ya hay y devuelve lo nuevo, sin tocar la
     * base de datos.
     *
     * Un enlace que ya tienes NO se actualiza, al revés que al guardarlo a mano: importar toca
     * cientos de golpe y sin que los veas pasar, y pisar títulos y etiquetas puestos a mano no se
     * deshace.
     */
    fun preparar(
        lectura: Lectura,
        existentes: List<Elemento>,
        ahora: Reloj = ::relojDelSistema,
        generarId: GeneradorId = ::generarIdUnico,
    ): Preparado {
        // Un conjunto con las direcciones ya normalizadas: comparar cada enlace contra la lista
        // entera tardaba segundos en Windows con unos cientos de cada lado.
        val vistas =
            existentes.filterNot { it.borrado }.map { Duplicados.normalizar(it.url) }.toMutableSet()
        val nuevos = mutableListOf<Elemento>()
        var yaEstaban = 0
        val instante = ahora()

        for (enlace in lectura.enlaces) {
            if (!vistas.add(Duplicados.normalizar(enlace.url))) {
                yaEstaban++
                continue
            }
            nuevos +=
                Elemento(
                    id = generarId(),
                    url = enlace.url,
                    titulo = enlace.titulo,
                    etiquetas = enlace.etiquetas,
                    // La fecha del fichero manda cuando la trae: si no, los ochocientos llegan
                    // con la de hoy y la lista pierde el orden.
                    creadoEn = enlace.creadoEn ?: instante,
                    actualizadoEn = instante,
                )
        }
        return Preparado(nuevos, yaEstaban, lectura.descartados)
    }

    // --- Reglas comunes ---

    /**
     * Solo direcciones de verdad. Los `javascript:` de los bookmarklets y los `place:` internos de
     * Firefox acabarían en la lista como enlaces que no abren nada.
     */
    private val esquemas = listOf("http://", "https://")

    internal fun esDireccion(texto: String): Boolean {
        val minusculas = texto.lowercase()
        return esquemas.any { minusculas.startsWith(it) && minusculas.length > it.length }
    }

    /** Dentro del propio fichero manda el primero, comparando como en el resto de la aplicación. */
    private fun quitarRepetidos(enlaces: List<EnlaceImportado>): Pair<List<EnlaceImportado>, Int> {
        val vistas = mutableSetOf<String>()
        val unicos = enlaces.filter { vistas.add(Duplicados.normalizar(it.url)) }
        return unicos to enlaces.size - unicos.size
    }

    /**
     * Sin repetir, y sin que cuenten mayúsculas ni tildes: «Postres» y «postres» son la misma, y se
     * conserva la primera que apareció.
     */
    internal fun etiquetasUnicas(nombres: List<String?>): List<String> {
        val vistas = mutableSetOf<String>()
        return nombres
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .filter { vistas.add(clave(it)) }
    }

    internal fun clave(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .trim()
            .lowercase()

    private fun primeraLineaConAlgo(contenido: String): String? =
        contenido.lineSequence().firstOrNull { it.isNotBlank() }

    // --- HTML de marcadores ---

    /**
     * Las carpetas raíz de un fichero de marcadores no distinguen nada: las lleva todo el mundo.
     * Una etiqueta que tienen cientos de enlaces es ruido.
     */
    private val carpetasQueNoSonEtiqueta =
        setOf("bookmarks", "marcadores", "bookmarks bar", "favoritos")

    /**
     * El formato de Netscape que exportan todos los navegadores.
     *
     * Las carpetas son `<H3>` y lo que contienen va dentro del `<DL>` que viene detrás, no dentro
     * del `<H3>`: el nombre se deja en espera y el `<DL>` lo mete en una pila.
     */
    private fun leerHtml(html: String): Pair<List<EnlaceImportado>, Int> {
        val enlaces = mutableListOf<EnlaceImportado>()
        var descartados = 0
        val pila = ArrayDeque<String?>()
        var pendiente: String? = null
        var pendienteEsBarra = false
        var enH3 = false
        var enA = false
        val texto = StringBuilder()
        var href = ""
        var fecha: MarcaDeTiempo? = null

        var i = 0
        while (i < html.length) {
            if (html[i] != '<') {
                val siguiente = html.indexOf('<', i).let { if (it < 0) html.length else it }
                if (enH3 || enA) texto.append(html, i, siguiente)
                i = siguiente
                continue
            }
            if (html.startsWith("<!--", i)) {
                val fin = html.indexOf("-->", i)
                i = if (fin < 0) html.length else fin + 3
                continue
            }
            val cierre = html.indexOf('>', i)
            if (cierre < 0) break
            val dentro = html.substring(i + 1, cierre)
            i = cierre + 1

            val cerrando = dentro.startsWith("/")
            val nombre = dentro.trimStart('/').takeWhile { it.isLetterOrDigit() }.lowercase()
            val atributos = if (cerrando) emptyMap() else leerAtributos(dentro)

            when {
                nombre == "dl" && !cerrando -> {
                    pila.addLast(pendiente)
                    pendiente = null
                }
                nombre == "dl" -> pila.removeLastOrNull()
                nombre == "h3" && !cerrando -> {
                    enH3 = true
                    texto.clear()
                    // Se reconoce por el atributo y no por el nombre, que cambia con el
                    // idioma del navegador.
                    pendienteEsBarra = atributos["personal_toolbar_folder"] == "true"
                }
                nombre == "h3" -> {
                    val carpeta = decodificar(texto.toString()).trim()
                    val descartar = pendienteEsBarra || clave(carpeta) in carpetasQueNoSonEtiqueta
                    pendiente = if (descartar) null else carpeta
                    enH3 = false
                    texto.clear()
                }
                nombre == "a" && !cerrando -> {
                    enA = true
                    texto.clear()
                    href = atributos["href"] ?: ""
                    fecha = segundosAMilisegundos(atributos["add_date"])
                }
                nombre == "a" -> {
                    enA = false
                    val direccion = href.trim()
                    val titulo = decodificar(texto.toString()).trim()
                    texto.clear()
                    if (direccion.isEmpty()) continue
                    if (!esDireccion(direccion)) {
                        descartados++
                        continue
                    }
                    enlaces +=
                        EnlaceImportado(
                            url = direccion,
                            titulo = titulo.ifEmpty { null },
                            etiquetas = etiquetasUnicas(listOf(pila.lastOrNull { it != null })),
                            creadoEn = fecha,
                        )
                }
            }
        }
        return enlaces to descartados
    }

    private val patronAtributo =
        Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")

    private fun leerAtributos(etiqueta: String): Map<String, String> =
        patronAtributo.findAll(etiqueta).associate { coincidencia ->
            val valor = coincidencia.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""
            coincidencia.groupValues[1].lowercase() to decodificar(valor)
        }

    private val entidades =
        mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

    /** `&amp;` es `&`. Si no, los títulos llegan con `&amp;` a la lista. */
    internal fun decodificar(texto: String): String {
        if ('&' !in texto) return texto
        val salida = StringBuilder()
        var i = 0
        while (i < texto.length) {
            val fin =
                if (texto[i] == '&') texto.indexOf(';', i).takeIf { it in i + 1..i + 11 } else null
            val valor = fin?.let { entidad(texto.substring(i + 1, it)) }
            if (fin != null && valor != null) {
                salida.append(valor)
                i = fin + 1
            } else {
                salida.append(texto[i])
                i++
            }
        }
        return salida.toString()
    }

    private fun entidad(nombre: String): String? {
        entidades[nombre.lowercase()]?.let {
            return it
        }
        if (!nombre.startsWith("#")) return null
        val numero = nombre.drop(1)
        val codigo =
            if (numero.startsWith("x", ignoreCase = true)) numero.drop(1).toIntOrNull(16)
            else numero.toIntOrNull()
        return codigo?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) }
    }

    // --- CSV ---

    /** Cada aplicación pone las columnas que quiere, así que se buscan por nombre. */
    private val columnas =
        mapOf(
            "url" to listOf("url", "link", "direccion", "dirección"),
            "titulo" to listOf("title", "titulo", "título", "name", "nombre"),
            "etiquetas" to listOf("tags", "etiquetas", "labels"),
            "carpeta" to listOf("folder", "carpeta"),
            "fecha" to listOf("time_added", "created", "date", "creado", "fecha"),
        )

    /**
     * Excel en español separa por punto y coma: la coma es su separador decimal. Se deduce de la
     * cabecera: el que haga aparecer la columna de direcciones.
     */
    private fun separador(cabecera: String): Char? {
        val direcciones = columnas.getValue("url").map(::clave).toSet()
        return listOf(',', ';', '\t').firstOrNull { separador ->
            separador in cabecera && cabecera.split(separador).any { clave(it) in direcciones }
        }
    }

    private fun leerCsv(contenido: String): Pair<List<EnlaceImportado>, Int> {
        val separador = primeraLineaConAlgo(contenido)?.let(::separador) ?: ','
        val filas = filasCsv(contenido, separador).filter { it != listOf("") }
        val cabecera = filas.firstOrNull() ?: return emptyList<EnlaceImportado>() to 0

        fun posicion(campo: String): Int? {
            val buscadas = columnas.getValue(campo).map(::clave).toSet()
            return cabecera.indexOfFirst { clave(it) in buscadas }.takeIf { it >= 0 }
        }
        val columnaUrl = posicion("url") ?: return emptyList<EnlaceImportado>() to 0
        val columnaTitulo = posicion("titulo")
        val columnaEtiquetas = posicion("etiquetas")
        val columnaCarpeta = posicion("carpeta")
        val columnaFecha = posicion("fecha")

        fun valor(fila: List<String>, columna: Int?) =
            columna?.let { fila.getOrNull(it) }?.trim() ?: ""

        val enlaces = mutableListOf<EnlaceImportado>()
        var descartados = 0
        for (fila in filas.drop(1)) {
            val url = valor(fila, columnaUrl)
            if (!esDireccion(url)) {
                descartados++
                continue
            }
            enlaces +=
                EnlaceImportado(
                    url = url,
                    titulo = valor(fila, columnaTitulo).ifEmpty { null },
                    // La carpeta primero: es la que ordena, y las etiquetas sueltas vienen
                    // detrás.
                    etiquetas =
                        etiquetasUnicas(
                            listOf(hoja(valor(fila, columnaCarpeta))) +
                                partirEtiquetas(valor(fila, columnaEtiquetas))
                        ),
                    creadoEn = fechaAMilisegundos(valor(fila, columnaFecha)),
                )
        }
        return enlaces to descartados
    }

    /**
     * Un CSV de verdad, con comillas: un separador o un salto de línea dentro de un campo entre
     * comillas es texto, y dos comillas seguidas son una.
     */
    internal fun filasCsv(texto: String, separador: Char): List<List<String>> {
        val filas = mutableListOf<List<String>>()
        var fila = mutableListOf<String>()
        val campo = StringBuilder()
        var entreComillas = false
        var i = 0

        while (i < texto.length) {
            val c = texto[i++]
            when {
                entreComillas && c == '"' && texto.getOrNull(i) == '"' -> {
                    campo.append('"')
                    i++
                }
                entreComillas && c == '"' -> entreComillas = false
                entreComillas -> campo.append(c)
                c == '"' -> entreComillas = true
                c == separador -> {
                    fila += campo.toString()
                    campo.clear()
                }
                c == '\r' || c == '\n' -> {
                    // Un «\r\n» de Windows no puede contar como dos filas.
                    if (c == '\r' && texto.getOrNull(i) == '\n') i++
                    fila += campo.toString()
                    filas += fila
                    fila = mutableListOf()
                    campo.clear()
                }
                else -> campo.append(c)
            }
        }
        if (campo.isNotEmpty() || fila.isNotEmpty()) {
            fila += campo.toString()
            filas += fila
        }
        return filas
    }

    /** Pocket separa por `|` y Raindrop por coma. Se aceptan las dos. */
    private fun partirEtiquetas(texto: String): List<String> =
        texto.split('|', ',').map(String::trim).filter(String::isNotEmpty)

    /** De «Recetas/Postres» se queda «Postres», que es la que dice algo. */
    private fun hoja(ruta: String): String? = ruta.split('/').lastOrNull()?.trim()

    // --- TXT ---

    private fun leerTxt(contenido: String): Pair<List<EnlaceImportado>, Int> {
        val enlaces = mutableListOf<EnlaceImportado>()
        var descartados = 0
        for (linea in contenido.lineSequence()) {
            if (linea.isBlank()) continue
            // La primera dirección de la línea, aunque venga dentro de una frase: mucha gente
            // guarda «Mira esto: https://…» tal cual.
            val url = linea.split(Regex("\\s+")).firstOrNull(::esDireccion)
            if (url == null) {
                descartados++
                continue
            }
            enlaces += EnlaceImportado(url = url)
        }
        return enlaces to descartados
    }

    // --- Fechas ---

    /** `ADD_DATE` y Pocket vienen en segundos; aquí todo va en milisegundos. */
    private fun segundosAMilisegundos(valor: String?): MarcaDeTiempo? =
        valor?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()?.times(1000)

    /** Pocket manda segundos; Raindrop, una fecha ISO. */
    private fun fechaAMilisegundos(valor: String): MarcaDeTiempo? {
        segundosAMilisegundos(valor)?.let {
            return it
        }
        if (valor.isEmpty()) return null
        val intentos =
            listOf<(String) -> MarcaDeTiempo>(
                { Instant.parse(it).toEpochMilli() },
                { OffsetDateTime.parse(it).toInstant().toEpochMilli() },
                // Sin zona se da por UTC, como hace Windows.
                { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC).toEpochMilli() },
            )
        for (intento in intentos) {
            try {
                return intento(valor)
            } catch (_: DateTimeParseException) {}
        }
        return null
    }
}
