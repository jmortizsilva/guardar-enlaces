import { Stack } from 'expo-router';

import { ProveedorApp } from '../src/contexto/ProveedorApp';

export default function LayoutRaiz() {
  return (
    <ProveedorApp>
      <Stack screenOptions={{ headerShown: false }} />
    </ProveedorApp>
  );
}
