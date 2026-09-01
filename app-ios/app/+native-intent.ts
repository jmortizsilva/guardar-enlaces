import { getScheme, getShareExtensionKey } from 'expo-share-intent';

import { marcarRutaCompartida } from '../src/compartir/pendiente';

/**
 * expo-router intenta resolver la URL de vuelta de la extension de compartir
 * (guardarenlaces:///dataUrl=...) como una ruta y falla con "Unmatched Route"
 * porque no existe ese path. Hay que redirigirla a una ruta real.
 *
 * expo-share-intent detecta esa misma URL por su cuenta via useLinkingURL(),
 * pero en pruebas reales no llego a dispararse (ni onChange ni onError).
 * Reconstruimos la URL canonica a mano (misma forma que arma la extension
 * nativa) y la dejamos preparada para que el layout raiz llame al modulo
 * nativo directamente en cuanto este montado y escuchando. Se guarda tambien
 * el path crudo para poder verlo en pantalla si algo no encaja: sin consola
 * de dispositivo (sin Mac) es la unica forma de depurar esto.
 */
export function redirectSystemPath({ path }: { path: string; initial: boolean }): string {
  try {
    const clave = getShareExtensionKey();
    if (path.includes(`dataUrl=${clave}`)) {
      const nonce = path.match(/nonce=([^&#]+)/)?.[1];
      const tipo = path.match(/#(\w+)/)?.[1] ?? 'weburl';
      marcarRutaCompartida({
        pathOriginal: path,
        rutaReconstruida: nonce ? `${getScheme()}://dataUrl=${clave}?nonce=${nonce}#${tipo}` : null,
      });
      return '/';
    }
    return path;
  } catch (error) {
    marcarRutaCompartida({ pathOriginal: `ERROR: ${String(error)}`, rutaReconstruida: null });
    return '/';
  }
}
