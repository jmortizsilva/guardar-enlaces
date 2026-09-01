import { Stack, useRouter } from 'expo-router';
import { ShareIntentModule, ShareIntentProvider } from 'expo-share-intent';
import { useEffect } from 'react';

import { tomarRutaCompartida } from '../src/compartir/pendiente';
import { ProveedorApp } from '../src/contexto/ProveedorApp';

export default function LayoutRaiz() {
  const router = useRouter();

  // Se ejecuta despues del efecto interno de ShareIntentProvider (hijo en el
  // arbol, monta primero): sus listeners onChange/onError ya estan activos.
  useEffect(() => {
    const ruta = tomarRutaCompartida();
    if (ruta) {
      // Igual que el uso interno de la propia libreria (useShareIntent.js):
      // sin await ni catch, el resultado real llega por el evento "onChange".
      ShareIntentModule?.getShareIntent(ruta);
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
