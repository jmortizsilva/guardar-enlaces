import { beforeEach, describe, expect, it } from 'vitest';

// config.ts lee ENLACES_TOKEN_SECRET del entorno SOLO la primera vez que se importa (el objeto
// `config` es una constante de modulo). Como '../db' importa '../config' transitivamente, hay que
// fijar el entorno ANTES de la primera importacion de cualquiera de los dos: por eso aqui, a
// diferencia de otros tests, ni siquiera 'db' se importa de forma estatica arriba.
const SECRETO = 'secreto-de-prueba';
let iniciarSesion: (typeof import('../auth/sesiones'))['iniciarSesion'];
let renovarSesion: (typeof import('../auth/sesiones'))['renovarSesion'];
let revocarSesion: (typeof import('../auth/sesiones'))['revocarSesion'];
let revocarTodasLasSesiones: (typeof import('../auth/sesiones'))['revocarTodasLasSesiones'];
let verificarTokenAcceso: (typeof import('../auth/tokenAcceso'))['verificarTokenAcceso'];

beforeEach(async () => {
  process.env.ENLACES_TOKEN_SECRET = SECRETO;
  const { inicializarBd } = await import('../db');
  const bd = inicializarBd(':memory:');
  // sesiones.usuario_id tiene FK a usuarios(id): los tests referencian los usuarios 1, 2 y 7.
  bd.prepare(
    `INSERT INTO usuarios (id, proveedor, id_proveedor, email, creado_en) VALUES
       (1, 'google', 'sub1', 'a@b.com', 0), (2, 'google', 'sub2', 'c@d.com', 0),
       (7, 'google', 'sub7', 'e@f.com', 0)`,
  ).run();
  const sesiones = await import('../auth/sesiones');
  ({ iniciarSesion, renovarSesion, revocarSesion, revocarTodasLasSesiones } = sesiones);
  ({ verificarTokenAcceso } = await import('../auth/tokenAcceso'));
});

describe('iniciarSesion', () => {
  it('emite un token de acceso valido y uno de refresco', () => {
    const tokens = iniciarSesion(7, 'iPhone de prueba');
    expect(verificarTokenAcceso(tokens.tokenAcceso, SECRETO)).toBe(7);
    expect(tokens.tokenRefresco).toHaveLength(64); // 32 bytes en hex
  });
});

describe('renovarSesion', () => {
  it('rota el token: el nuevo funciona, el viejo deja de servir', () => {
    const primeros = iniciarSesion(7, null);
    const renovados = renovarSesion(primeros.tokenRefresco);
    expect(renovados).toBeDefined();
    expect(verificarTokenAcceso(renovados!.tokenAcceso, SECRETO)).toBe(7);

    // reutilizar el token de refresco ya rotado (robado o reenviado) falla
    expect(renovarSesion(primeros.tokenRefresco)).toBeUndefined();
  });

  it('un token de refresco desconocido no renueva nada', () => {
    expect(renovarSesion('token-que-no-existe')).toBeUndefined();
  });
});

describe('revocarSesion', () => {
  it('tras revocar, renovar con ese token falla', () => {
    const tokens = iniciarSesion(7, null);
    revocarSesion(tokens.tokenRefresco);
    expect(renovarSesion(tokens.tokenRefresco)).toBeUndefined();
  });
});

describe('revocarTodasLasSesiones', () => {
  it('revoca todas las sesiones activas de un usuario, no las de otro', () => {
    const a1 = iniciarSesion(1, 'dispositivo A');
    const a2 = iniciarSesion(1, 'dispositivo B');
    const b1 = iniciarSesion(2, 'dispositivo de otro usuario');

    revocarTodasLasSesiones(1);

    expect(renovarSesion(a1.tokenRefresco)).toBeUndefined();
    expect(renovarSesion(a2.tokenRefresco)).toBeUndefined();
    expect(renovarSesion(b1.tokenRefresco)).toBeDefined();
  });
});
