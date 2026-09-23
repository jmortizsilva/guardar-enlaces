package com.jmortizsilva.guardarenlaces.dominio

/**
 * Saber si una URL ya está guardada.
 *
 * Comparar las URL tal cual no sirve: la misma página llega con «www» o sin él, con http o con
 * https, con una barra final de más, con un ancla, o arrastrando los parámetros de seguimiento que
 * añaden las redes sociales y los boletines. Todo eso es el mismo enlace para una persona, que es
 * quien va a oír el aviso.
 *
 * Lo que no se toca: las mayúsculas de la ruta (hay servidores donde sí distinguen) ni los
 * parámetros que de verdad identifican el contenido.
 */
object Duplicados {
    private val prefijosDeSeguimiento = listOf("utm_")
    private val parametrosDeSeguimiento =
        setOf("fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "ref", "ref_src", "si")

    private fun esDeSeguimiento(clave: String): Boolean {
        val minuscula = clave.lowercase()
        return minuscula in parametrosDeSeguimiento ||
            prefijosDeSeguimiento.any { minuscula.startsWith(it) }
    }

    /**
     * Forma canónica para comparar, no para guardar ni para abrir: se queda sin esquema a
     * propósito, porque http y https son la misma página.
     *
     * Lo que no se puede analizar se devuelve tal cual en minúsculas: mejor no detectar un
     * duplicado que inventarse uno.
     */
    fun normalizar(url: String): String {
        val limpia = url.trim()
        val partes = PartesDeUrl.leer(limpia)
        val anfitrion = partes?.anfitrion?.lowercase()
        if (partes == null || anfitrion.isNullOrEmpty()) {
            return limpia.lowercase()
        }

        val host = anfitrion.removePrefix("www.")
        val ruta = partes.ruta.trimEnd('/')

        // `sortedBy` es estable: dos parámetros con la misma clave conservan el orden en que
        // vinieron. En Swift hubo que desempatar a mano porque `sorted` no lo promete.
        val parametros =
            partes.parametros
                .filterNot { esDeSeguimiento(it.first) }
                .sortedBy { it.first }
                .joinToString("&") { "${it.first}=${it.second.orEmpty()}" }

        return if (parametros.isEmpty()) "$host$ruta" else "$host$ruta?$parametros"
    }

    fun esLaMisma(una: String, otra: String): Boolean = normalizar(una) == normalizar(otra)

    /**
     * El elemento ya guardado con esa misma URL, si lo hay. Los borrados no cuentan: si lo tiraste,
     * volver a guardarlo es un alta normal.
     */
    fun buscar(en: List<Elemento>, url: String): Elemento? {
        val buscada = normalizar(url)
        return en.firstOrNull { !it.borrado && normalizar(it.url) == buscada }
    }
}
