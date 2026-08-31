import { getShareExtensionKey } from 'expo-share-intent';

/**
 * expo-router intenta resolver la URL de vuelta de la extension de compartir
 * (guardarenlaces:///dataUrl=...) como una ruta y falla con "Unmatched Route"
 * porque no existe ese path. Hay que redirigirla a una ruta real para que el
 * ShareIntentProvider (que escucha la misma URL) pueda recoger el contenido.
 */
export function redirectSystemPath({ path }: { path: string; initial: boolean }): string {
  try {
    if (path.includes(`dataUrl=${getShareExtensionKey()}`)) {
      return '/';
    }
    return path;
  } catch {
    return '/';
  }
}
