import { Stack, useRouter } from 'expo-router';
import { ShareIntentProvider } from 'expo-share-intent';

import { ProveedorApp } from '../src/contexto/ProveedorApp';

export default function LayoutRaiz() {
  const router = useRouter();

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
