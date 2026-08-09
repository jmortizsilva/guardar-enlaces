/**
 * Guarda el token de refresco en el Keychain de iOS via expo-secure-store,
 * nunca en AsyncStorage/fichero plano. El token de ACCESO (corta duracion)
 * no se persiste aqui: solo vive en memoria mientras la app esta abierta.
 */
import * as SecureStore from 'expo-secure-store';

const CLAVE_TOKEN_REFRESCO = 'guardar-enlaces-token-refresco';

export function guardarTokenRefresco(token: string): Promise<void> {
  return SecureStore.setItemAsync(CLAVE_TOKEN_REFRESCO, token);
}

export function obtenerTokenRefresco(): Promise<string | null> {
  return SecureStore.getItemAsync(CLAVE_TOKEN_REFRESCO);
}

export async function borrarTokenRefresco(): Promise<void> {
  try {
    await SecureStore.deleteItemAsync(CLAVE_TOKEN_REFRESCO);
  } catch {
    // no habia nada guardado: cerrar sesion sin haber iniciado sesion es un no-op
  }
}
