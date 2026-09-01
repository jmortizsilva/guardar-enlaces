import { Redirect, Stack } from 'expo-router';
import { ActivityIndicator, View } from 'react-native';

import { useSesion } from '../../src/contexto/ProveedorApp';
import { useTema } from '../../src/interfaz/tema';

/** Grupo protegido: sin sesion, redirige a /login antes de montar nada de dentro. */
export default function LayoutApp() {
  const tema = useTema();
  const sesion = useSesion();

  if (sesion.cargando) {
    return (
      <View
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

  if (!sesion.autenticado) {
    return <Redirect href="/login" />;
  }

  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="anadir" options={{ presentation: 'modal' }} />
    </Stack>
  );
}
