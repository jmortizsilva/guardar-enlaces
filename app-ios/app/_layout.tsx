import { Stack } from 'expo-router';
import { ShareIntentProvider } from 'expo-share-intent';
import { useEffect } from 'react';
import { AppState } from 'react-native';

import {
  avisarSiHayNovedades,
  comprobarActualizacion,
} from '../src/actualizaciones/actualizaciones';
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
