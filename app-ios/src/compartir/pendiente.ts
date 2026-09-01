/**
 * Puente entre app/+native-intent.ts (se ejecuta antes de que monte nada de
 * React, no puede llamar al modulo nativo sin perder el evento) y el layout
 * raiz (donde expo-share-intent ya esta escuchando). Modulo JS = singleton,
 * vive mientras dure el proceso.
 */
let rutaCompartida: string | null = null;

export function marcarRutaCompartida(ruta: string): void {
  rutaCompartida = ruta;
}

export function tomarRutaCompartida(): string | null {
  const ruta = rutaCompartida;
  rutaCompartida = null;
  return ruta;
}
