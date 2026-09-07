import { getShareExtensionKey } from 'expo-share-intent';

/**
 * expo-router intenta resolver la URL de vuelta de la extension de compartir
 * (guardarenlaces:///dataUrl=...) como una ruta y falla con "Unmatched Route"
 * porque no existe ese path. Hay que redirigirla a una ruta real; el propio
 * expo-share-intent detecta esa misma URL por su cuenta via useLinkingURL()
 * en cuanto cambia, sin necesidad de nada mas aqui.
 */
export function redirectSystemPath({ path }: { path: string; initial: boolean }): string {
  const clave = getShareExtensionKey();
  return path.includes(`dataUrl=${clave}`) ? '/' : path;
}
