import { Stack } from 'expo-router';
import { ShareIntentModule, ShareIntentProvider } from 'expo-share-intent';
import { useEffect } from 'react';
import { Alert, AppState } from 'react-native';

import {
  avisarSiHayNovedades,
  comprobarActualizacion,
} from '../src/actualizaciones/actualizaciones';
import { tomarRutaCompartida } from '../src/compartir/pendiente';
import { ProveedorApp } from '../src/contexto/ProveedorApp';

export default function LayoutRaiz() {
  // Al abrir: si esta build ya vino de una OTA distinta a la ultima vista,
  // avisa de las novedades; despues busca si hay otra actualizacion nueva.
  useEffect(() => {
    avisarSiHayNovedades().then(() => comprobarActualizacion());
  }, []);

  // Al volver a primer plano (no solo al abrir en frio): patron de
  // comun/docs/GUIA-ENTORNO-IOS.md, es el disparador fiable del dialogo.
  useEffect(() => {
    const subscripcion = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') {
        comprobarActualizacion();
      }
    });
    return () => subscripcion.remove();
  }, []);

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
      }}>
      <ProveedorApp>
        <Stack screenOptions={{ headerShown: false }} />
      </ProveedorApp>
    </ShareIntentProvider>
  );
}
