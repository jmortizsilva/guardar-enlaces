import { config } from './config';
import { inicializarBd } from './db';
import { crearServidor } from './servidor';

// Variables sin las que el servidor arrancaria pero fallaria en silencio (tokens firmados con
// secreto vacio, OAuth incapaz de completar el intercambio): mejor no arrancar en absoluto.
function comprobarConfiguracionMinima(): void {
  const faltantes: string[] = [];
  if (!config.tokenSecreto) faltantes.push('ENLACES_TOKEN_SECRET');
  if (!config.google.clientId) faltantes.push('GOOGLE_CLIENT_ID');
  if (!config.google.clientSecret) faltantes.push('GOOGLE_CLIENT_SECRET');
  if (!config.apple.clientId) faltantes.push('APPLE_CLIENT_ID');
  if (!config.apple.teamId) faltantes.push('APPLE_TEAM_ID');
  if (!config.apple.keyId) faltantes.push('APPLE_KEY_ID');
  if (!config.apple.privateKey) faltantes.push('APPLE_PRIVATE_KEY');
  if (faltantes.length > 0) {
    throw new Error(`faltan variables de entorno: ${faltantes.join(', ')}`);
  }
}

async function main(): Promise<void> {
  comprobarConfiguracionMinima();
  inicializarBd();
  const app = await crearServidor();
  await app.listen({ port: config.puerto, host: '0.0.0.0' });
  app.log.info(`servidor de guardar-enlaces escuchando en ${config.puerto}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
