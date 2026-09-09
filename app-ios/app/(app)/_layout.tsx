import { Stack } from 'expo-router';
import { ActivityIndicator, View } from 'react-native';

import { useSesion } from '../../src/contexto/ProveedorApp';
import { useTema } from '../../src/interfaz/tema';

/**
 * La app NO exige cuenta: se abre directamente en la lista y funciona entera
 * en local. La cuenta solo hace falta para sincronizar con el PC, y se inicia
 * sesion desde Ajustes.
 *
 * Aqui solo queda la espera del arranque: mientras se intenta restaurar una
 * sesion guardada no se sabe todavia si esto va a ir en local o con cuenta, y
 * montar la lista antes haria que se recolocara sola al terminar.
 */
export default function LayoutApp() {
  const tema = useTema();
  const sesion = useSesion();

  if (sesion.cargando) {
    return (
      <View
        accessibilityRole="progressbar"
        accessibilityLabel="Abriendo"
        style={{
          flex: 1,
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: tema.fondo,
        }}>
        <ActivityIndicator color={tema.acento} />
      </View>
    );
  }

  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="anadir" options={{ presentation: 'modal' }} />
    </Stack>
  );
}
