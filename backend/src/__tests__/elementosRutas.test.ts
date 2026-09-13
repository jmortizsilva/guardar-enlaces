import Fastify, { FastifyInstance } from 'fastify';
import { beforeAll, describe, expect, it } from 'vitest';

// El secreto de tokens se lee del entorno al importar config, asi que hay que fijarlo ANTES de
// importar cualquier modulo que dependa de el (por eso los imports son dinamicos en beforeAll),
// igual que ya hace servidor-notificaciones con su clave de app.
const SECRETO = 'secreto-de-prueba';
let app: FastifyInstance;
let emitirTokenAcceso: (typeof import('../auth/tokenAcceso'))['emitirTokenAcceso'];

beforeAll(async () => {
  process.env.ENLACES_TOKEN_SECRET = SECRETO;
  const db = await import('../db');
  db.inicializarBd(':memory:');
  obtenerBdDePrueba(db);

  const { registrarRutasElementos } = await import('../elementos/rutas');
  ({ emitirTokenAcceso } = await import('../auth/tokenAcceso'));

  app = Fastify();
  await app.register(registrarRutasElementos);
});

function obtenerBdDePrueba(db: typeof import('../db')): void {
  db.obtenerBd()
    .prepare(
      `INSERT INTO usuarios (id, proveedor, id_proveedor, email, creado_en) VALUES (1, 'google', 'sub1', 'a@b.com', 0)`,
    )
    .run();
}

function tokenValido(usuarioId = 1): string {
  return emitirTokenAcceso(usuarioId, Date.now() + 60_000, SECRETO);
}

describe('rutas de elementos', () => {
  it('rechaza sin token (401)', async () => {
    const res = await app.inject({ method: 'GET', url: '/sincronizar' });
    expect(res.statusCode).toBe(401);
  });

  it('rechaza token invalido (401)', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/sincronizar',
      headers: { authorization: 'Bearer basura' },
    });
    expect(res.statusCode).toBe(401);
  });

  it('push valido seguido de pull devuelve el elemento', async () => {
    const auth = { authorization: `Bearer ${tokenValido()}` };

    const push = await app.inject({
      method: 'POST',
      url: '/sincronizar',
      headers: auth,
      payload: { elementos: [{ id: 'e1', url: 'https://a.com', titulo: 'A', actualizadoEn: 100 }] },
    });
    expect(push.statusCode).toBe(200);
    expect(push.json().elementos[0].titulo).toBe('A');

    const pull = await app.inject({
      method: 'GET',
      url: '/sincronizar?desde=0',
      headers: auth,
    });
    expect(pull.statusCode).toBe(200);
    expect(pull.json().elementos).toHaveLength(1);
  });

  it('push sin cuerpo de elementos ni etiquetas (400)', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/sincronizar',
      headers: { authorization: `Bearer ${tokenValido()}` },
      payload: {},
    });
    expect(res.statusCode).toBe(400);
  });

  it('push solo de etiquetasDefinidas (sin elementos) seguido de pull las devuelve', async () => {
    const auth = { authorization: `Bearer ${tokenValido()}` };

    const push = await app.inject({
      method: 'POST',
      url: '/sincronizar',
      headers: auth,
      payload: { etiquetasDefinidas: [{ id: 't1', nombre: 'ocio', actualizadoEn: 100 }] },
    });
    expect(push.statusCode).toBe(200);
    expect(push.json().etiquetasDefinidas[0].nombre).toBe('ocio');
    expect(push.json().elementos).toEqual([]);

    const pull = await app.inject({
      method: 'GET',
      url: '/sincronizar?desde=0',
      headers: auth,
    });
    expect(pull.statusCode).toBe(200);
    expect(pull.json().etiquetasDefinidas).toHaveLength(1);
  });
});
