package com.jmortizsilva.guardarenlaces

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier

/** Qué hay copiado, sabido sin leerlo. */
enum class Copiado {
    Nada,
    Texto,
    Enlace,
}

/**
 * El portapapeles, en dos pasos a propósito: mirar qué hay sin leerlo, y leerlo solo cuando se
 * pulsa «Pegar».
 *
 * Mirar la descripción no lee el contenido, y no saca el aviso del sistema de que la app ha pegado
 * algo; leerlo, desde Android 12, sí. En iOS lo resuelve el botón de pegar del sistema, que aquí no
 * existe.
 *
 * Android solo deja consultar el portapapeles a la app que tiene el foco de la ventana: antes de
 * tenerlo, esto dice que no hay nada.
 */
object Portapapeles {
    fun queHay(contexto: Context): Copiado {
        val portapapeles = contexto.getSystemService(ClipboardManager::class.java)
        val descripcion = portapapeles.primaryClipDescription ?: return Copiado.Nada
        val esTexto =
            descripcion.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                descripcion.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
        if (!esTexto) return Copiado.Nada
        // Desde Android 12 el sistema clasifica lo copiado y dice si parece una dirección, sin que
        // la app lo lea. Antes no se puede saber sin leerlo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val clasificado =
                descripcion.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
            if (clasificado && descripcion.getConfidenceScore(TextClassifier.TYPE_URL) > 0f) {
                return Copiado.Enlace
            }
        }
        return Copiado.Texto
    }

    /** Lee lo copiado. Aquí es donde Android 12 o posterior avisa de que la app ha pegado. */
    fun leer(contexto: Context): String? {
        val portapapeles = contexto.getSystemService(ClipboardManager::class.java)
        return portapapeles.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(contexto)
            ?.toString()
    }
}
