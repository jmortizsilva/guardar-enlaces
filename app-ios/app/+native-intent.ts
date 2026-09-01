import { getScheme, getShareExtensionKey } from 'expo-share-intent';

import { marcarRutaCompartida } from '../src/compartir/pendiente';

/**
 * expo-router intenta resolver la URL de vuelta de la extension de compartir
 * (guardarenlaces:///dataUrl=...) como una ruta y falla con "Unmatched Route"
 * porque no existe ese path. Hay que redirigirla a una ruta real.
 *
 * expo-share-intent detecta esa misma URL por su cuenta via useLinkingURL(),
 * pero en pruebas reales no llego a dispararse (ni onChange ni onError), y
 * tampoco la version anterior de este fichero (que solo guardaba algo si
 * ".includes(dataUrl=...)" encajaba). Sin consola de dispositivo (sin Mac),
 * la unica forma de ver que esta pasando de verdad es que la app lo diga:
 * ahora se guarda SIEMPRE el path recibido, en todos los arranques (incluido
 * uno normal tocando el icono), para comprobar primero si esta funcion
 * llega a ejecutarse siquiera. Ruidoso a proposito, solo para esta ronda de
 * pruebas: quitar en cuanto se confirme que pasa de verdad.
 */
export function redirectSystemPath({ path, initial }: { path: string; initial: boolean }): string {
  try {
    const clave = getShareExtensionKey();
    if (path.includes(`dataUrl=${clave}`)) {
      const nonce = path.match(/nonce=([^&#]+)/)?.[1];
      const tipo = path.match(/#(\w+)/)?.[1] ?? 'weburl';
      marcarRutaCompartida({
        pathOriginal: `[COMPARTIR, initial=${initial}] ${path}`,
        rutaReconstruida: nonce ? `${getScheme()}://dataUrl=${clave}?nonce=${nonce}#${tipo}` : null,
      });
      return '/';
    }
    marcarRutaCompartida({
      pathOriginal: `[normal, initial=${initial}] ${path}`,
      rutaReconstruida: null,
    });
    return path;
  } catch (error) {
    marcarRutaCompartida({ pathOriginal: `ERROR: ${String(error)}`, rutaReconstruida: null });
    return '/';
  }
}
