import { getScheme, getShareExtensionKey } from 'expo-share-intent';

import { marcarRutaCompartida } from '../src/compartir/pendiente';

/**
 * expo-router intenta resolver la URL de vuelta de la extension de compartir
 * (guardarenlaces:///dataUrl=...) como una ruta y falla con "Unmatched Route"
 * porque no existe ese path. Hay que redirigirla a una ruta real.
 *
 * expo-share-intent detecta esa misma URL por su cuenta via useLinkingURL(),
 * pero en pruebas reales no llego a dispararse (ni onChange ni onError): la
 * forma exacta de la URL que ve expo-router aqui (en "path") no coincide con
 * el "scheme://dataUrl=..." de dos barras que espera esa comprobacion.
 * Como aqui SI llega de forma fiable (basta con .includes), reconstruimos la
 * URL canonica a mano - misma forma que construye la extension nativa - y la
 * dejamos preparada para que el layout raiz llame al modulo nativo en cuanto
 * este montado y escuchando.
 */
export function redirectSystemPath({ path }: { path: string; initial: boolean }): string {
  try {
    const clave = getShareExtensionKey();
    if (path.includes(`dataUrl=${clave}`)) {
      const nonce = path.match(/nonce=([^&#]+)/)?.[1];
      const tipo = path.match(/#(\w+)/)?.[1] ?? 'weburl';
      if (nonce) {
        marcarRutaCompartida(`${getScheme()}://dataUrl=${clave}?nonce=${nonce}#${tipo}`);
      }
      return '/';
    }
    return path;
  } catch {
    return '/';
  }
}
