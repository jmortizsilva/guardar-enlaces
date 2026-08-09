import { beforeEach, describe, expect, it } from 'vitest';
import { inicializarBd } from '../db';
import {
  buscarLoginPendiente,
  consumirCodigoCanje,
  crearLoginPendiente,
  marcarErrorLoginPendiente,
  resolverLoginPendiente,
} from '../auth/loginPendientes';

beforeEach(() => {
  const bd = inicializarBd(':memory:');
  // login_pendientes.usuario_id tiene FK a usuarios(id): los tests usan el usuario 42.
  bd.prepare(
    `INSERT INTO usuarios (id, proveedor, id_proveedor, email, creado_en) VALUES (42, 'google', 'sub42', 'a@b.com', 0)`,
  ).run();
});

describe('crearLoginPendiente / buscarLoginPendiente', () => {
  it('se puede recuperar recien creado, todavia sin resolver', () => {
    crearLoginPendiente('estado1', 'polling', null);
    const fila = buscarLoginPendiente('estado1');
    expect(fila?.modo).toBe('polling');
    expect(fila?.codigo_canje).toBeNull();
    expect(fila?.error).toBeNull();
  });

  it('caducado no se puede recuperar', () => {
    const ahora = () => 1_000_000;
    crearLoginPendiente('estado1', 'polling', null, ahora);
    expect(buscarLoginPendiente('estado1', () => 1_000_000 + 6 * 60 * 1000)).toBeUndefined();
  });

  it('desconocido no se puede recuperar', () => {
    expect(buscarLoginPendiente('no-existe')).toBeUndefined();
  });
});

describe('resolverLoginPendiente / consumirCodigoCanje', () => {
  it('genera un codigo de canje que consumirCodigoCanje resuelve al usuario y solo una vez', () => {
    crearLoginPendiente('estado1', 'deeplink', 'guardarenlaces://');
    const codigo = resolverLoginPendiente('estado1', 42);

    expect(buscarLoginPendiente('estado1')?.codigo_canje).toBe(codigo);
    expect(consumirCodigoCanje(codigo)).toBe(42);
    // segunda vez ya no existe: se borro al consumirlo
    expect(consumirCodigoCanje(codigo)).toBeUndefined();
    expect(buscarLoginPendiente('estado1')).toBeUndefined();
  });

  it('un codigo desconocido no resuelve nada', () => {
    expect(consumirCodigoCanje('codigo-inventado')).toBeUndefined();
  });
});

describe('marcarErrorLoginPendiente', () => {
  it('deja el error visible para quien consulte el estado', () => {
    crearLoginPendiente('estado1', 'polling', null);
    marcarErrorLoginPendiente('estado1', 'sin_invitacion');
    expect(buscarLoginPendiente('estado1')?.error).toBe('sin_invitacion');
  });
});
