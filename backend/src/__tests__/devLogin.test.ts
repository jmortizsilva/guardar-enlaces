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
    const { registrarRutasAuth } = await import('../auth/rutas');
    app = Fastify();
    await app.register(registrarRutasAuth);
  });

  it('crea la cuenta y devuelve tokens para cualquier correo', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/dev-login',
      payload: { email: 'persona@x.com' },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().usuario.email).toBe('persona@x.com');
    expect(res.json().tokenAcceso).toBeTruthy();
  });

  it('rechaza algo que no sea un correo (400)', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/dev-login',
      payload: { email: 'no-es-un-correo' },
    });
    expect(res.statusCode).toBe(400);
  });
});
