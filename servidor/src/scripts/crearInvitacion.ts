// Añade un correo a la lista de invitados: npm run crear-invitacion -- correo@ejemplo.com
// No hace falta contraseña que generar ni imprimir: la persona simplemente entra con su cuenta
// Google o Apple la primera vez y, si su correo esta invitado, se le crea la cuenta sola.

import { inicializarBd } from '../db';
import { estaInvitado, invitar } from '../auth/usuarios';

function main(): void {
  const email = process.argv[2];
  if (!email || !email.includes('@')) {
    console.error('uso: npm run crear-invitacion -- correo@ejemplo.com');
    process.exit(1);
  }

  inicializarBd();
  if (estaInvitado(email)) {
    console.log(`${email} ya estaba invitado.`);
    return;
  }
  invitar(email);
  console.log(`${email} invitado. Ya puede entrar con Google o Apple usando ese correo.`);
}

main();
