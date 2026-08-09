import formbody from '@fastify/formbody';
import rateLimit from '@fastify/rate-limit';
import Fastify, { FastifyInstance } from 'fastify';
import { registrarRutasAuth } from './auth/rutas';
import { registrarRutasElementos } from './elementos/rutas';
import { registrarRutasMetadatos } from './metadatos/rutas';

export async function crearServidor(): Promise<FastifyInstance> {
  const app = Fastify({ logger: true, bodyLimit: 1_000_000 });

  // Limite general de abuso (igual que servidor-notificaciones); /auth/iniciar y /auth/canjear
  // llevan ademas su propio limite mas estricto, definido en sus rutas.
  await app.register(rateLimit, { max: 120, timeWindow: '1 minute' });

  // Apple manda el callback OAuth por POST con application/x-www-form-urlencoded
  // (response_mode=form_post), asi que hace falta parsear ese tipo de cuerpo.
  await app.register(formbody);

  app.get('/', async () => ({ ok: true, service: 'guardar-enlaces' }));

  await app.register(registrarRutasAuth);
  await app.register(registrarRutasElementos);
  await app.register(registrarRutasMetadatos);

  return app;
}
