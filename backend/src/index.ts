import { config } from './config';
import { inicializarBd } from './db';
import { crearServidor } from './servidor';

// Variables sin las que el servidor arrancaria pero fallaria en silencio (tokens firmados con
// secreto vacio, OAuth incapaz de completar el intercambio): mejor no arrancar en absoluto, y que
// se note al desplegar y no cuando alguien intente entrar.
//
// Apple se exige desde el 2026-09-30, igual que Google. Hasta entonces solo avisaba, porque se
// probo primero Google con las credenciales de Apple sin tramitar. El aviso de antes miraba
// cuatro variables y se le escapaba APPLE_APP_ID, sin la que no funciona entrar con Apple desde
// el iPhone (es la audiencia del token nativo).
function comprobarConfiguracionMinima(): void {
  const obligatorias: [string, unknown][] = [
    ['ENLACES_TOKEN_SECRET', config.tokenSecreto],
    ['GOOGLE_CLIENT_ID', config.google.clientId],
    ['GOOGLE_CLIENT_SECRET', config.google.clientSecret],
    ['APPLE_CLIENT_ID', config.apple.clientId],
    ['APPLE_APP_ID', config.apple.appIds.length > 0],
    ['APPLE_TEAM_ID', config.apple.teamId],
    ['APPLE_KEY_ID', config.apple.keyId],
    ['APPLE_PRIVATE_KEY', config.apple.privateKey],
  ];
  const faltantes = obligatorias.filter(([, valor]) => !valor).map(([nombre]) => nombre);
  if (faltantes.length > 0) {
    throw new Error(`faltan variables de entorno: ${faltantes.join(', ')}`);
  }
}

async function main(): Promise<void> {
  comprobarConfiguracionMinima();
  const bd = inicializarBd();
  const app = await crearServidor();

  // Podman para el contenedor con SIGTERM y, si en 10 s no ha salido, lo mata con SIGKILL. Node,
  // como primer proceso del contenedor, no tiene respuesta por defecto a SIGTERM: sin esto se
  // quedaba esos 10 s esperando y moria a la fuerza, a mitad de lo que estuviera escribiendo
  // (visto en el despliegue del 2026-09-30). Ahora deja de aceptar peticiones, termina las que
  // tiene en curso y cierra la base de datos.
  for (const senal of ['SIGTERM', 'SIGINT'] as const) {
    process.once(senal, () => {
      app.log.info(`${senal}: cerrando`);
      app
        .close()
        .then(() => {
          bd.close();
          process.exit(0);
        })
        .catch((error: unknown) => {
          app.log.error(error);
          process.exit(1);
        });
    });
  }

  await app.listen({ port: config.puerto, host: '0.0.0.0' });
  app.log.info(`servidor de guardar-enlaces escuchando en ${config.puerto}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
