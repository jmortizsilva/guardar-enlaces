import { config } from './config';
import { inicializarBd } from './db';
import { crearServidor } from './servidor';

// Variables sin las que el servidor arrancaria pero fallaria en silencio (tokens firmados con
// secreto vacio, OAuth incapaz de completar el intercambio): mejor no arrancar en absoluto.
//
// Apple queda TEMPORALMENTE fuera de esta comprobacion (solo avisa, no bloquea el arranque):
// se esta probando primero el login real con Google, con las credenciales de Apple todavia
// sin tramitar. /auth/iniciar?proveedor=apple ya rechaza la peticion mientras falten (ver
// rutas.ts). Volver a exigirlas aqui en cuanto Apple este listo.
function comprobarConfiguracionMinima(): void {
  const faltantes: string[] = [];
  if (!config.tokenSecreto) faltantes.push('ENLACES_TOKEN_SECRET');
  if (!config.google.clientId) faltantes.push('GOOGLE_CLIENT_ID');
  if (!config.google.clientSecret) faltantes.push('GOOGLE_CLIENT_SECRET');
  if (faltantes.length > 0) {
    throw new Error(`faltan variables de entorno: ${faltantes.join(', ')}`);
  }
  if (
    !config.apple.clientId ||
    !config.apple.teamId ||
    !config.apple.keyId ||
    !config.apple.privateKey
  ) {
    console.warn(
      'Aviso: faltan variables de Apple (APPLE_CLIENT_ID/TEAM_ID/KEY_ID/PRIVATE_KEY) — ' +
        'el login con Apple no funcionara hasta que se configuren.',
    );
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
