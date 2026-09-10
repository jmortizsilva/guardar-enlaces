import formbody from '@fastify/formbody';
import Fastify, { FastifyInstance } from 'fastify';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const SECRETO = 'secreto-de-prueba';

vi.mock('../auth/oauth', async () => {
  const real = await vi.importActual<typeof import('../auth/oauth')>('../auth/oauth');
  return {
    ...real,
    intercambiarCodigoGoogle: vi.fn(async (codigo: string) => {
      if (codigo === 'codigo-con-email') {
        return { proveedor: 'google' as const, idProveedor: 'sub-1', email: 'persona@x.com', emailVerificado: true };
      }
      if (codigo === 'codigo-sin-email') {
        return { proveedor: 'google' as const, idProveedor: 'sub-2', email: null, emailVerificado: false };
      }
      throw new Error('codigo de prueba desconocido');
    }),
    intercambiarCodigoApple: vi.fn(async () => {
      throw new Error('no usado en estos tests');
    }),
  };
});

let app: FastifyInstance;
let inicializarBd: (typeof import('../db'))['inicializarBd'];

beforeAll(async () => {
  process.env.ENLACES_TOKEN_SECRET = SECRETO;
  process.env.URL_PUBLICA = 'https://servidor.ejemplo.com';
  process.env.GOOGLE_CLIENT_ID = 'g-client';
  process.env.GOOGLE_CLIENT_SECRET = 'g-secret';

  const db = await import('../db');
  ({ inicializarBd } = db);
  const { registrarRutasAuth } = await import('../auth/rutas');

  app = Fastify();
  await app.register(formbody);
  await app.register(registrarRutasAuth);
});

beforeEach(() => {
  inicializarBd(':memory:');
});

describe('GET /auth/iniciar', () => {
  it('redirige a Google con el mismo estado', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/auth/iniciar?proveedor=google&modo=deeplink&estado=e1&esquema=guardarenlaces',
    });
    expect(res.statusCode).toBe(302);
    const destino = new URL(res.headers.location as string);
    expect(destino.hostname).toBe('accounts.google.com');
    expect(destino.searchParams.get('state')).toBe('e1');
  });

  it('modo invalido -> 400', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/auth/iniciar?proveedor=google&modo=lo-que-sea&estado=e1',
    });
    expect(res.statusCode).toBe(400);
  });

  it('deeplink sin esquema -> 400', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/auth/iniciar?proveedor=google&modo=deeplink&estado=e1',
    });
    expect(res.statusCode).toBe(400);
  });
});

describe('flujo completo (modo deeplink)', () => {
  it('callback redirige al esquema con un codigo que se canjea por tokens', async () => {
    await app.inject({
      method: 'GET',
      url: '/auth/iniciar?proveedor=google&modo=deeplink&estado=e2&esquema=guardarenlaces',
    });

    const callback = await app.inject({
      method: 'GET',
      url: '/auth/callback/google?code=codigo-con-email&state=e2',
    });
    expect(callback.statusCode).toBe(302);
    const destino = new URL(callback.headers.location as string);
    expect(destino.protocol).toBe('guardarenlaces:');
    const codigoCanje = destino.searchParams.get('codigo');
    expect(codigoCanje).toBeTruthy();

    const canjear = await app.inject({
      method: 'POST',
      url: '/auth/canjear',
      payload: { codigoCanje },
    });
    expect(canjear.statusCode).toBe(200);
    const cuerpo = canjear.json();
    expect(cuerpo.usuario.email).toBe('persona@x.com');
    expect(cuerpo.tokenAcceso).toBeTruthy();
    expect(cuerpo.tokenRefresco).toBeTruthy();
  });
});

describe('flujo completo (modo polling, proveedor que no da correo)', () => {
  it('callback marca error y /auth/estado lo refleja', async () => {
    await app.inject({
      method: 'GET',
      url: '/auth/iniciar?proveedor=google&modo=polling&estado=e3',
    });

    const callback = await app.inject({
      method: 'GET',
      url: '/auth/callback/google?code=codigo-sin-email&state=e3',
    });
    expect(callback.statusCode).toBe(200);

    const estado = await app.inject({ method: 'GET', url: '/auth/estado?estado=e3' });
    expect(estado.json()).toEqual({ listo: true, error: 'sin_email' });
  });
});

describe('POST /auth/renovar', () => {
  it('devuelve tambien el usuario, para que el cliente sepa con que cuenta esta', async () => {
    await app.inject({
      method: 'GET',
      url: '/auth/iniciar?proveedor=google&modo=polling&estado=e9',
    });
    await app.inject({ method: 'GET', url: '/auth/callback/google?code=codigo-con-email&state=e9' });
    const estado = await app.inject({ method: 'GET', url: '/auth/estado?estado=e9' });
    const canje = await app.inject({
      method: 'POST',
      url: '/auth/canjear',
      payload: { codigoCanje: estado.json().codigoCanje },
    });

    const renovado = await app.inject({
      method: 'POST',
      url: '/auth/renovar',
      payload: { tokenRefresco: canje.json().tokenRefresco },
    });

    expect(renovado.statusCode).toBe(200);
    expect(renovado.json().usuario.email).toBe('persona@x.com');
    // Y no se cuela el identificador interno que usan las rutas por dentro.
    expect(renovado.json().usuarioId).toBeUndefined();
  });
});

describe('POST /auth/canjear', () => {
  it('codigo invalido -> 400', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/canjear',
      payload: { codigoCanje: 'no-existe' },
    });
    expect(res.statusCode).toBe(400);
  });
});

describe('POST /auth/renovar y logout', () => {
  it('renueva un token de refresco valido; tras logout ya no renueva', async () => {
    await app.inject({
      method: 'GET',
      url: '/auth/iniciar?proveedor=google&modo=polling&estado=e4',
    });
    await app.inject({ method: 'GET', url: '/auth/callback/google?code=codigo-con-email&state=e4' });
    const { codigoCanje } = (await app.inject({ method: 'GET', url: '/auth/estado?estado=e4' })).json();
    const { tokenRefresco } = (
      await app.inject({ method: 'POST', url: '/auth/canjear', payload: { codigoCanje } })
    ).json();

    const renovar = await app.inject({
      method: 'POST',
      url: '/auth/renovar',
      payload: { tokenRefresco },
    });
    expect(renovar.statusCode).toBe(200);
    const nuevoRefresco = renovar.json().tokenRefresco;

    await app.inject({ method: 'POST', url: '/auth/logout', payload: { tokenRefresco: nuevoRefresco } });

    const renovarTrasLogout = await app.inject({
      method: 'POST',
      url: '/auth/renovar',
      payload: { tokenRefresco: nuevoRefresco },
    });
    expect(renovarTrasLogout.statusCode).toBe(401);
  });

  it('token de refresco invalido -> 401', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/auth/renovar',
      payload: { tokenRefresco: 'inventado' },
    });
    expect(res.statusCode).toBe(401);
  });
});
