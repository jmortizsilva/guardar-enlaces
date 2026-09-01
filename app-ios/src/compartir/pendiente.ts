/**
 * Puente entre app/+native-intent.ts (se ejecuta antes de que monte nada de
 * React, no puede llamar al modulo nativo sin perder el evento) y el layout
 * raiz (donde expo-share-intent ya esta escuchando). Modulo JS = singleton,
 * vive mientras dure el proceso.
 */

interface RutaCompartidaDebug {
  /** Ruta cruda tal cual la ve expo-router, antes de reconstruir nada. */
  pathOriginal: string;
  /** URL reconstruida a mano para el modulo nativo, o null si no se pudo. */
  rutaReconstruida: string | null;
}

let pendiente: RutaCompartidaDebug | null = null;

export function marcarRutaCompartida(debug: RutaCompartidaDebug): void {
  pendiente = debug;
}

export function tomarRutaCompartida(): RutaCompartidaDebug | null {
  const valor = pendiente;
  pendiente = null;
  return valor;
}
