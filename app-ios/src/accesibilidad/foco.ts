/**
 * Devolver el foco de VoiceOver a un elemento concreto.
 *
 * Al cerrar un diálogo o al desaparecer una fila, iOS manda el foco al
 * principio de la pantalla y el usuario pierde su sitio.
 *
 * Es una promesa a propósito: mover el foco corta cualquier anuncio en curso,
 * así que primero se espera a que el foco esté puesto y solo después se
 * anuncia el resultado de la acción.
 */

import { AccessibilityInfo, type HostInstance } from 'react-native';

/** Margen para que el foco no se pida antes de que la pantalla se haya recompuesto. */
const MS_ESPERA_CIERRE = 300;

/**
 * Se usa sendAccessibilityEvent y NO setAccessibilityFocus.
 *
 * setAccessibilityFocus está marcada como obsoleta y por dentro delega en
 * legacySendAccessibilityEvent, que el propio código de React Native describe
 * como la vía "para el renderer pre-Fabric, sobre nodos pre-Fabric". Con la
 * arquitectura nueva (la de este proyecto) no encuentra la vista y no hace
 * nada, sin dar ningún error: parece que funciona y el foco nunca se mueve.
 *
 * sendAccessibilityEvent recibe la referencia del componente directamente, sin
 * findNodeHandle de por medio.
 */
export function devolverFoco(elemento: HostInstance | null | undefined): Promise<void> {
  return new Promise((resolver) => {
    setTimeout(() => {
      if (elemento) AccessibilityInfo.sendAccessibilityEvent(elemento, 'focus');
      resolver();
    }, MS_ESPERA_CIERRE);
  });
}
