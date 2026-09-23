package com.jmortizsilva.guardarenlaces

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull

/**
 * Lleva el cursor de TalkBack al nodo con esa `testTag`. Devuelve si lo encontró.
 *
 * Hace falta porque `FocusRequester.requestFocus()` mueve el foco del teclado, no el de TalkBack.
 * Medido el 2026-09-23 en un Pixel 10a con TalkBack 17: tras eliminar una fila y pedir el foco en
 * la siguiente, TalkBack se iba al título de la pantalla. La prueba del Mac pasaba, porque ella sí
 * mira el foco del teclado.
 *
 * Compose no tiene una forma pública de mover el foco de accesibilidad. Esto le pide la acción
 * `ACTION_ACCESSIBILITY_FOCUS` al proveedor de accesibilidad de la vista, que es lo mismo que haría
 * TalkBack; lo hacía también la app de Expo con `sendAccessibilityEvent`. Para dar con el nodo se
 * usa `RootForTest`, una interfaz pública de Compose pensada para las pruebas: si un día deja de
 * estar, esto devuelve `false` y el cursor se queda donde lo deje el sistema.
 */
fun View.llevarCursorDeTalkBack(etiqueta: String): Boolean {
    val raiz = this as? RootForTest ?: return false
    val nodo =
        raiz.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).firstOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag) == etiqueta
        } ?: return false
    return accessibilityNodeProvider?.performAction(
        nodo.id,
        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
        null,
    ) ?: false
}
