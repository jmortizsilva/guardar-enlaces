import type { FastifyInstance } from 'fastify';
import { beforeAll, describe, expect, it } from 'vitest';

let app: FastifyInstance;

beforeAll(async () => {
  process.env.ENLACES_TOKEN_SECRET = 'secreto-de-prueba';
  process.env.URL_PUBLICA = 'https://servidor.ejemplo.com';
  process.env.GOOGLE_CLIENT_ID = 'g-client';
  process.env.GOOGLE_CLIENT_SECRET = 'g-secret';
  process.env.APPLE_CLIENT_ID = 'com.ejemplo.servicios';
  process.env.APPLE_TEAM_ID = 'EQUIPO123';
  process.env.APPLE_KEY_ID = 'CLAVE123';
  process.env.APPLE_PRIVATE_KEY = '-----BEGIN PRIVATE KEY-----\nfalsa\n-----END PRIVATE KEY-----';

  const db = await import('../db');
  db.inicializarBd(':memory:');
  const { crearServidor } = await import('../servidor');
  app = await crearServidor();
});

describe('servidor', () => {
  it('GET / responde salud', async () => {
    const res = await app.inject({ method: 'GET', url: '/' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ ok: true, service: 'guardar-enlaces' });
  });

  it('las rutas de auth, elementos y metadatos estan registradas', async () => {
    const sinToken = await app.inject({ method: 'GET', url: '/sincronizar' });
    expect(sinToken.statusCode).toBe(401); // registrada, pero exige sesion

    const iniciar = await app.inject({
      method: 'GET',
      url: '/auth/iniciar?proveedor=google&modo=polling&estado=e1',
    });
    expect(iniciar.statusCode).toBe(302); // registrada, redirige a Google

    const metadatos = await app.inject({ method: 'POST', url: '/metadatos', payload: {} });
    expect(metadatos.statusCode).toBe(401); // registrada, pero exige sesion
  });
});
