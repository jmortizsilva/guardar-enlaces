import { obtenerBd } from '../db';
import { PerfilOAuth } from './oauth';

export interface Usuario {
  id: number;
  email: string;
  proveedor: string;
}

export function estaInvitado(email: string): boolean {
  const fila = obtenerBd()
    .prepare('SELECT 1 FROM invitados WHERE email = ?')
    .get(email.toLowerCase());
  return fila !== undefined;
}

export function invitar(email: string, ahora: () => number = () => Date.now()): void {
  obtenerBd()
    .prepare('INSERT OR IGNORE INTO invitados (email, invitado_en) VALUES (?, ?)')
    .run(email.toLowerCase(), ahora());
}

interface FilaUsuario {
  id: number;
  email: string;
  proveedor: string;
}

// Busca el usuario por (proveedor, idProveedor); si no existe, lo crea, PERO SOLO SI su email
// esta en la lista de invitados. Devuelve undefined si no tiene invitacion: la puerta de entrada
// al sistema sin tener que gestionar contrasenas (ver contexto en docs/CONTRATO-API.md).
export function obtenerOCrearUsuario(
  perfil: PerfilOAuth,
  ahora: () => number = () => Date.now(),
): Usuario | undefined {
  const bd = obtenerBd();
  const existente = bd
    .prepare('SELECT id, email, proveedor FROM usuarios WHERE proveedor = ? AND id_proveedor = ?')
    .get(perfil.proveedor, perfil.idProveedor) as FilaUsuario | undefined;
  if (existente) {
    return existente;
  }

  if (!perfil.email || !estaInvitado(perfil.email)) {
    return undefined;
  }

  const resultado = bd
    .prepare(
      `INSERT INTO usuarios (proveedor, id_proveedor, email, creado_en) VALUES (?, ?, ?, ?)`,
    )
    .run(perfil.proveedor, perfil.idProveedor, perfil.email, ahora());
  return { id: Number(resultado.lastInsertRowid), email: perfil.email, proveedor: perfil.proveedor };
}

export function obtenerUsuarioPorId(id: number): Usuario | undefined {
  return obtenerBd().prepare('SELECT id, email, proveedor FROM usuarios WHERE id = ?').get(id) as
    | Usuario
    | undefined;
}
