import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { crearExigirSesion } from '../auth/middleware';
import { config } from '../config';
import { resolverMetadatos, UrlNoPermitidaError } from './resolver';

interface CuerpoMetadatos {
  url?: unknown;
}

export async function registrarRutasMetadatos(app: FastifyInstance): Promise<void> {
  const exigirSesion = crearExigirSesion(config.tokenSecreto!);

  app.post<{ Body: CuerpoMetadatos }>(
    '/metadatos',
    { preHandler: exigirSesion },
    async (request: FastifyRequest<{ Body: CuerpoMetadatos }>, reply: FastifyReply) => {
      const urlTexto = request.body?.url;
      if (typeof urlTexto !== 'string' || urlTexto.length === 0) {
        return reply.code(400).send({ error: 'falta la url' });
      }

      let url: URL;
      try {
        url = new URL(urlTexto);
      } catch {
        return reply.code(400).send({ error: 'url invalida' });
      }
      if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        return reply.code(400).send({ error: 'solo se admiten URLs http/https' });
      }

      try {
        return await resolverMetadatos(urlTexto);
      } catch (error) {
        if (error instanceof UrlNoPermitidaError) {
          return reply.code(400).send({ error: 'url no permitida' });
        }
        request.log.warn({ error, url: urlTexto }, 'no se pudieron resolver los metadatos');
        return reply.code(502).send({ error: 'no se pudo obtener la vista previa de esa url' });
      }
    },
  );
}
