import Fastify, { FastifyInstance } from 'fastify';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const SECRETO = 'secreto-de-prueba';

// config.ts lee PERMITIR_LOGIN_DEV en una constante de modulo: hace falta vi.resetModules() para
// que una reimportacion vuelva a leer el entorno, en vez de reusar el modulo ya cacheado.
describe('POST /auth/dev-login', () => {
  it('sin PERMITIR_LOGIN_DEV, la ruta ni existe (404)', async () => {
    vi.resetModules();
    delete process.env.PERMITIR_LOGIN_DEV;
    process.env.ENLACES_TOKEN_SECRET = SECRETO;
    const { registrarRutasAuth } = await import('../auth/rutas');
    const app = Fastify();
    await app.register(registrarRutasAuth);

    const res = await app.inject({
      method: 'POST',
      url: '/auth/dev-login',
      payload: { email: 'x@y.com' },
    });
    expect(res.statusCode).toBe(404);
  });
});

describe('POST /auth/dev-login con PERMITIR_LOGIN_DEV=true', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    vi.resetModules();
    process.env.PERMITIR_LOGIN_DEV = 'true';
    process.env.ENLACES_TOKEN_SECRET = SECRETO;
    const db = await import('../db');
    db.inicializarBd(':memory:');
    const { invitar } = await import('../auth/usuarios');
    invitar('invitado@x.com');
    const { registrarRutasAuth } = await import('../auth/rutas');
    app = Fastify();
    await app.register(registrarRutasAuth);
  });

  it('devuelve tokens para un correo invitado', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/dev-login',
      payload: { email: 'invitado@x.com' },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().usuario.email).toBe('invitado@x.com');
    expect(res.json().tokenAcceso).toBeTruthy();
  });

  it('rechaza un correo no invitado (403)', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/dev-login',
      payload: { email: 'nadie@x.com' },
    });
    expect(res.statusCode).toBe(403);
  });
});
