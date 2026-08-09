/**
 * Anuncios de voz.
 *
 * Regla del proyecto: solo se anuncian cambios de estado importantes
 * (conectado, error, eliminado). La telemetría NO se anuncia continuamente:
 * sería inutilizable. Se lee al enfocar cada valor.
 *
 * El turno importa más de lo que parece. Un anuncio en cola lo corta cualquier
 * cosa, incluido un cambio de foco: al eliminar una cinta, la fila desaparece,
 * el foco salta y el "cinta eliminada" se queda a medias. Emitirlo SIN `queue`
 * (se lee ya, saltando la cola) es lo que evita que se pierda.
 *
 * OJO con la versión de RN: `priority` depende de la versión. En 0.81 el nativo de
 * iOS (RCTAccessibilityManager.mm) SOLO lee `queue` y el tipado no declara
 * `priority` —pasarlo rompe el typecheck—; en 0.86 sí existe. Aquí se usa solo
 * `queue` para ser portable a la RN más baja que se soporte. Ver el recuadro de
 * "Prioridad" en GUIA-ACCESIBILIDAD-RN.md.
 */

import { useEffect, useState } from 'react';
import { AccessibilityInfo } from 'react-native';

/**
 * Informativo. Espera su turno y no pisa nada de lo que se esté leyendo.
 * Para avisos que el usuario puede perderse sin consecuencias.
 */
export function anunciar(mensaje: string): void {
  AccessibilityInfo.announceForAccessibilityWithOptions(mensaje, { queue: true });
}

/**
 * Resultado de una acción o error. Se emite ya (sin `queue`), saltando la cola,
 * así que no lo pisa ni un cambio de foco: el usuario lo oye entero.
 *
 * Aun así conviene mover el foco ANTES de llamar aquí: si el foco se mueve
 * mientras habla, el orden de lo que se oye deja de tener sentido.
 */
export function anunciarImportante(mensaje: string): void {
  AccessibilityInfo.announceForAccessibilityWithOptions(mensaje, { queue: false });
}

export function useLectorPantallaActivo(): boolean {
  const [activo, setActivo] = useState(false);

  useEffect(() => {
    let vigente = true;
    AccessibilityInfo.isScreenReaderEnabled().then((valor) => {
      if (vigente) setActivo(valor);
    });
    const suscripcion = AccessibilityInfo.addEventListener('screenReaderChanged', setActivo);
    return () => {
      vigente = false;
      suscripcion.remove();
    };
  }, []);

  return activo;
}
