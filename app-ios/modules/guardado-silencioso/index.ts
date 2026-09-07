import { requireNativeModule } from 'expo-modules-core';
import { Platform } from 'react-native';

interface ModuloNativo {
  obtenerModoSilencioso(): boolean;
  establecerModoSilencioso(valor: boolean): void;
}

/**
 * Puente al interruptor nativo: el valor vive en el App Group compartido
 * (UserDefaults) para que ShareExtension lo lea sin pasar por React Native.
 * En plataformas sin módulo nativo (web) se queda siempre en false.
 */
const modulo =
  Platform.OS === 'ios' ? requireNativeModule<ModuloNativo>('GuardadoSilencioso') : null;

export async function obtenerModoSilencioso(): Promise<boolean> {
  return modulo?.obtenerModoSilencioso() ?? false;
}

export async function establecerModoSilencioso(valor: boolean): Promise<void> {
  modulo?.establecerModoSilencioso(valor);
}
