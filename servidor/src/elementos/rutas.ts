import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { crearExigirSesion } from '../auth/middleware';
import { config } from '../config';
import { ElementoEntrada, pull, push } from './almacen';

interface QuerySincronizar {
  desde?: string;
  limite?: string;
}

interface CuerpoSincronizar {
  elementos?: unknown;
}

const LIMITE_POR_DEFECTO = 300;
const LIMITE_MAXIMO = 1000;

function limiteValido(texto: string | undefined): number {
  const n = texto ? Number(texto) : LIMITE_POR_DEFECTO;
  if (!Number.isFinite(n) || n <= 0) {
    return LIMITE_POR_DEFECTO;
  }
  return Math.min(n, LIMITE_MAXIMO);
}

// Valida el formato minimo de una entrada del lote de push. No valida url como URL "de verdad"
// aqui (eso ya lo hizo el cliente al pedir /metadatos); solo que el contrato basico se cumple.
function entradaValida(x: unknown): x is ElementoEntrada {
  if (typeof x !== 'object' || x === null) {
    return false;
  }
  const e = x as Record<string, unknown>;
  return (
    typeof e.id === 'string' &&
    e.id.length > 0 &&
    typeof e.actualizadoEn === 'number' &&
    (e.url === undefined || typeof e.url === 'string') &&
    (e.etiquetas === undefined || Array.isArray(e.etiquetas))
  );
}

export async function registrarRutasElementos(app: FastifyInstance): Promise<void> {
  const exigirSesion = crearExigirSesion(config.tokenSecreto!);

  app.get<{ Querystring: QuerySincronizar }>(
    '/sincronizar',
    { preHandler: exigirSesion },
    async (request: FastifyRequest<{ Querystring: QuerySincronizar }>) => {
      const desde = Number(request.query.desde ?? 0) || 0;
      const limite = limiteValido(request.query.limite);
      return pull(request.usuarioId!, desde, limite);
    },
  );

  app.post<{ Body: CuerpoSincronizar }>(
    '/sincronizar',
    { preHandler: exigirSesion },
    async (request: FastifyRequest<{ Body: CuerpoSincronizar }>, reply: FastifyReply) => {
      const entradas = request.body?.elementos;
      if (!Array.isArray(entradas) || entradas.length === 0) {
        return reply.code(400).send({ error: 'falta el lote de elementos' });
      }
      if (entradas.length > LIMITE_MAXIMO) {
        return reply.code(400).send({ error: `maximo ${LIMITE_MAXIMO} elementos por lote` });
      }
      if (!entradas.every(entradaValida)) {
        return reply.code(400).send({ error: 'entrada de elemento invalida' });
      }
      const definitivos = push(request.usuarioId!, entradas as ElementoEntrada[]);
      return { elementos: definitivos };
    },
  );
}
