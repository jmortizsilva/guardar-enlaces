import { Stack, useRouter } from 'expo-router';
import { ShareIntentModule, ShareIntentProvider } from 'expo-share-intent';
import { useEffect } from 'react';
import { Alert } from 'react-native';

import { tomarRutaCompartida } from '../src/compartir/pendiente';
import { ProveedorApp } from '../src/contexto/ProveedorApp';

export default function LayoutRaiz() {
  const router = useRouter();

  // Se ejecuta despues del efecto interno de ShareIntentProvider (hijo en el
  // arbol, monta primero): sus listeners onChange/onError ya estan activos.
  useEffect(() => {
    const debug = tomarRutaCompartida();
    if (!debug) return;

    // TEMPORAL: sin consola de dispositivo, ensena en pantalla lo que ha
    // recibido de verdad para poder ajustarlo con datos reales. Quitar en
    // cuanto compartir funcione de forma fiable.
    Alert.alert(
      'Debug: enlace compartido',
      `path: ${debug.pathOriginal}\n\nreconstruida: ${debug.rutaReconstruida ?? '(no se pudo)'}`,
    );

    if (debug.rutaReconstruida) {
      // Igual que el uso interno de la propia libreria (useShareIntent.js):
      // sin await ni catch, el resultado real llega por el evento "onChange".
      ShareIntentModule?.getShareIntent(debug.rutaReconstruida);
    }
  }, []);

  return (
    <ShareIntentProvider
      options={{
        resetOnBackground: true,
        onResetShareIntent: () => router.replace('/'),
      }}>
      <ProveedorApp>
        <Stack screenOptions={{ headerShown: false }} />
      </ProveedorApp>
    </ShareIntentProvider>
  );
}
