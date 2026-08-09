import Fastify, { FastifyInstance } from 'fastify';
import { beforeAll, describe, expect, it, vi } from 'vitest';

const SECRETO = 'secreto-de-prueba';
let app: FastifyInstance;
let emitirTokenAcceso: (typeof import('../auth/tokenAcceso'))['emitirTokenAcceso'];

vi.mock('../metadatos/resolver', async () => {
  const real = await vi.importActual<typeof import('../metadatos/resolver')>(
    '../metadatos/resolver',
  );
  return {
    ...real,
    resolverMetadatos: vi.fn(async (url: string) => {
      if (url.includes('falla')) {
        throw new Error('fallo de red simulado');
      }
      if (url.includes('privada')) {
        throw new real.UrlNoPermitidaError('privada');
      }
      return { titulo: 'Vista previa', descripcion: null, imagenUrl: null, tipo: 'enlace' };
    }),
  };
});

beforeAll(async () => {
  process.env.ENLACES_TOKEN_SECRET = SECRETO;
  const { registrarRutasMetadatos } = await import('../metadatos/rutas');
  ({ emitirTokenAcceso } = await import('../auth/tokenAcceso'));
  app = Fastify();
  await app.register(registrarRutasMetadatos);
});

function auth() {
  return { authorization: `Bearer ${emitirTokenAcceso(1, Date.now() + 60_000, SECRETO)}` };
}

describe('POST /metadatos', () => {
  it('rechaza sin token (401)', async () => {
    const res = await app.inject({ method: 'POST', url: '/metadatos', payload: { url: 'https://a.com' } });
    expect(res.statusCode).toBe(401);
  });

  it('rechaza sin url (400)', async () => {
    const res = await app.inject({ method: 'POST', url: '/metadatos', headers: auth(), payload: {} });
    expect(res.statusCode).toBe(400);
  });

  it('rechaza una url mal formada (400)', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/metadatos',
      headers: auth(),
      payload: { url: 'no-es-una-url' },
    });
    expect(res.statusCode).toBe(400);
  });

  it('devuelve los metadatos resueltos', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/metadatos',
      headers: auth(),
      payload: { url: 'https://a.com/pagina' },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().titulo).toBe('Vista previa');
  });

  it('url que resuelve a red privada -> 400', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/metadatos',
      headers: auth(),
      payload: { url: 'https://privada.example.com' },
    });
    expect(res.statusCode).toBe(400);
  });

  it('fallo de red al resolver -> 502', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/metadatos',
      headers: auth(),
      payload: { url: 'https://falla.example.com' },
    });
    expect(res.statusCode).toBe(502);
  });
});
