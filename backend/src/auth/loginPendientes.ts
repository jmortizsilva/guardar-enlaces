import { randomBytes } from 'node:crypto';
import { obtenerBd } from '../db';

// El "buzon" del login OAuth: /auth/iniciar crea la fila, /auth/callback/:proveedor la rellena
// (codigo de canje + usuario, o el motivo del fallo), y /auth/estado (modo
// polling) o el redirect a esquema:// (modo deeplink) se la entregan al cliente. /auth/canjear la
// consume una sola vez. Vida corta: se limpia sola por caducidad, no hace falta tarea de fondo
// para el MVP (una purga periodica queda para la Fase 2).

// Lo que tiene quien inicia sesion desde que se abre la pestana hasta que vuelve del proveedor.
// Eran 5 minutos y no bastaban: el 2026-09-30, entrar con Apple desde Android con TalkBack
// (contrasena, codigo de verificacion en otro dispositivo) tardo 8 min 57 s, y al volver el
// servidor ya no lo encontraba y ensenaba "Enlace caducado". Subirlo no debilita nada: la fila se
// encuentra por un `estado` aleatorio de un solo uso, y el codigo de canje tiene su propio minuto.
export const DURACION_LOGIN_PENDIENTE_MS = 15 * 60 * 1000;

// Lo que dura el codigo de canje desde que se genera, que es lo que dice el contrato (~60 s). Antes
// no tenia caducidad propia: heredaba la de la fila, y con ella valia hasta 5 minutos despues de
// abrir la pestana. Separarlos es lo que permite dar mas tiempo a la pestana sin alargar la vida de
// un codigo que, en Android sin Auth Tab, otra app podria llegar a ver.
export const DURACION_CODIGO_CANJE_MS = 60 * 1000;

export type ModoLogin = 'deeplink' | 'polling';

export function crearLoginPendiente(
  estado: string,
  modo: ModoLogin,
  esquema: string | null,
  ahora: () => number = () => Date.now(),
): void {
  obtenerBd()
    .prepare(
      `INSERT INTO login_pendientes (estado, modo, esquema, creado_en, expira_en)
       VALUES (@estado, @modo, @esquema, @creadoEn, @expiraEn)
       ON CONFLICT(estado) DO UPDATE SET
         modo = @modo, esquema = @esquema, creado_en = @creadoEn, expira_en = @expiraEn,
         codigo_canje = NULL, usuario_id = NULL, error = NULL`,
    )
    .run({
      estado,
      modo,
      esquema,
      creadoEn: ahora(),
      expiraEn: ahora() + DURACION_LOGIN_PENDIENTE_MS,
    });
}

export interface FilaLoginPendiente {
  estado: string;
  modo: ModoLogin;
  esquema: string | null;
  expira_en: number;
  codigo_canje: string | null;
  usuario_id: number | null;
  error: string | null;
}

export function buscarLoginPendiente(
  estado: string,
  ahora: () => number = () => Date.now(),
): FilaLoginPendiente | undefined {
  const fila = obtenerBd()
    .prepare('SELECT * FROM login_pendientes WHERE estado = ?')
    .get(estado) as FilaLoginPendiente | undefined;
  if (!fila || fila.expira_en <= ahora()) {
    return undefined;
  }
  return fila;
}

// Genera el codigo de canje de un solo uso y lo asocia al usuario que acaba de iniciar sesion. Desde
// aqui, la fila vive lo que el codigo: un minuto.
export function resolverLoginPendiente(
  estado: string,
  usuarioId: number,
  ahora: () => number = () => Date.now(),
): string {
  const codigoCanje = randomBytes(24).toString('base64url');
  obtenerBd()
    .prepare(
      'UPDATE login_pendientes SET codigo_canje = ?, usuario_id = ?, expira_en = ? WHERE estado = ?',
    )
    .run(codigoCanje, usuarioId, ahora() + DURACION_CODIGO_CANJE_MS, estado);
  return codigoCanje;
}

export function marcarErrorLoginPendiente(estado: string, error: string): void {
  obtenerBd().prepare('UPDATE login_pendientes SET error = ? WHERE estado = ?').run(error, estado);
}

// Consume el codigo de canje (un solo uso): lo busca, borra la fila y devuelve el usuarioId.
export function consumirCodigoCanje(
  codigoCanje: string,
  ahora: () => number = () => Date.now(),
): number | undefined {
  const bd = obtenerBd();
  const fila = bd
    .prepare('SELECT * FROM login_pendientes WHERE codigo_canje = ?')
    .get(codigoCanje) as FilaLoginPendiente | undefined;
  if (!fila || fila.expira_en <= ahora() || fila.usuario_id == null) {
    return undefined;
  }
  bd.prepare('DELETE FROM login_pendientes WHERE estado = ?').run(fila.estado);
  return fila.usuario_id;
}
