import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { config } from '../config';
import { obtenerBd } from '../db';
import { emitirTokenAcceso } from './tokenAcceso';

// Sesiones = tokens de REFRESCO (los de acceso son sin estado, ver tokenAcceso.ts). Se guardan
// hasheados (sha256 basta: el token en si ya es un secreto aleatorio de alta entropia, no una
// contrasena elegida por una persona, asi que no hace falta un hash "lento" tipo scrypt).

export const DURACION_ACCESO_MS = 2 * 60 * 60 * 1000; // 2 horas
export const DURACION_REFRESCO_MS = 90 * 24 * 60 * 60 * 1000; // 90 dias

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

function generarTokenRefresco(): string {
  return randomBytes(32).toString('hex');
}

function crearSesion(
  usuarioId: number,
  dispositivo: string | null,
  ahora: () => number,
): string {
  const tokenRefresco = generarTokenRefresco();
  obtenerBd()
    .prepare(
      `INSERT INTO sesiones (id, usuario_id, hash_token_refresco, dispositivo, creado_en, expira_en)
       VALUES (@id, @usuarioId, @hash, @dispositivo, @creadoEn, @expiraEn)`,
    )
    .run({
      id: randomUUID(),
      usuarioId,
      hash: hashToken(tokenRefresco),
      dispositivo,
      creadoEn: ahora(),
      expiraEn: ahora() + DURACION_REFRESCO_MS,
    });
  return tokenRefresco;
}

interface FilaSesion {
  id: string;
  usuario_id: number;
  dispositivo: string | null;
  expira_en: number;
  revocado_en: number | null;
}

function sesionPorToken(tokenRefresco: string): FilaSesion | undefined {
  return obtenerBd()
    .prepare('SELECT * FROM sesiones WHERE hash_token_refresco = ?')
    .get(hashToken(tokenRefresco)) as FilaSesion | undefined;
}

export interface ParDeTokens {
  tokenAcceso: string;
  expiraEn: number; // caducidad del token de ACCESO (el de refresco no se expone al cliente)
  tokenRefresco: string;
}

function emitirParDeTokens(
  usuarioId: number,
  dispositivo: string | null,
  ahora: () => number,
): ParDeTokens {
  const tokenRefresco = crearSesion(usuarioId, dispositivo, ahora);
  const expiraEn = ahora() + DURACION_ACCESO_MS;
  const tokenAcceso = emitirTokenAcceso(usuarioId, expiraEn, config.tokenSecreto ?? '');
  return { tokenAcceso, expiraEn, tokenRefresco };
}

// Primer login: crea la sesion (refresco) y el primer token de acceso para un usuario ya
// resuelto por el flujo OAuth.
export function iniciarSesion(
  usuarioId: number,
  dispositivo: string | null,
  ahora: () => number = () => Date.now(),
): ParDeTokens {
  return emitirParDeTokens(usuarioId, dispositivo, ahora);
}

// Valida un token de refresco y lo ROTA: revoca la sesion actual y crea una nueva para el mismo
// usuario/dispositivo. Asi un token de refresco robado deja rastro: si se usa dos veces (el
// legitimo y el atacante, en cualquier orden), la segunda vez ya esta revocado y falla aqui.
export function renovarSesion(
  tokenRefrescoActual: string,
  ahora: () => number = () => Date.now(),
): ParDeTokens | undefined {
  const fila = sesionPorToken(tokenRefrescoActual);
  if (!fila || fila.revocado_en != null || fila.expira_en <= ahora()) {
    return undefined;
  }
  obtenerBd()
    .prepare('UPDATE sesiones SET revocado_en = ?, ultimo_uso_en = ? WHERE id = ?')
    .run(ahora(), ahora(), fila.id);
  return emitirParDeTokens(fila.usuario_id, fila.dispositivo, ahora);
}

export function revocarSesion(tokenRefresco: string, ahora: () => number = () => Date.now()): void {
  const fila = sesionPorToken(tokenRefresco);
  if (fila && fila.revocado_en == null) {
    obtenerBd().prepare('UPDATE sesiones SET revocado_en = ? WHERE id = ?').run(ahora(), fila.id);
  }
}

export function revocarTodasLasSesiones(
  usuarioId: number,
  ahora: () => number = () => Date.now(),
): void {
  obtenerBd()
    .prepare('UPDATE sesiones SET revocado_en = ? WHERE usuario_id = ? AND revocado_en IS NULL')
    .run(ahora(), usuarioId);
}
