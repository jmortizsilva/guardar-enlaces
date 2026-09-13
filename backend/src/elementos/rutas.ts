import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { crearExigirSesion } from '../auth/middleware';
import { config } from '../config';
import {
  EtiquetaEntrada,
  pull as pullEtiquetas,
  push as pushEtiquetas,
} from '../etiquetas/almacen';
import { ElementoEntrada, pull, push } from './almacen';

interface QuerySincronizar {
  desde?: string;
  limite?: string;
}

interface CuerpoSincronizar {
  elementos?: unknown;
  etiquetasDefinidas?: unknown;
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

function entradaEtiquetaValida(x: unknown): x is EtiquetaEntrada {
  if (typeof x !== 'object' || x === null) {
    return false;
  }
  const e = x as Record<string, unknown>;
  return (
    typeof e.id === 'string' &&
    e.id.length > 0 &&
    typeof e.actualizadoEn === 'number' &&
    (e.nombre === undefined || typeof e.nombre === 'string')
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
      const resultado = pull(request.usuarioId!, desde, limite);
      const { etiquetasDefinidas } = pullEtiquetas(request.usuarioId!, desde);
      return { ...resultado, etiquetasDefinidas };
    },
  );

  app.post<{ Body: CuerpoSincronizar }>(
    '/sincronizar',
    { preHandler: exigirSesion },
    async (request: FastifyRequest<{ Body: CuerpoSincronizar }>, reply: FastifyReply) => {
      const entradas = request.body?.elementos;
      const entradasEtiquetas = request.body?.etiquetasDefinidas;
      const hayElementos = entradas !== undefined;
      const hayEtiquetas = entradasEtiquetas !== undefined;

      if (!hayElementos && !hayEtiquetas) {
        return reply.code(400).send({ error: 'falta el lote de elementos o de etiquetas' });
      }
      if (hayElementos && (!Array.isArray(entradas) || entradas.length === 0)) {
        return reply.code(400).send({ error: 'falta el lote de elementos' });
      }
      if (hayElementos && (entradas as unknown[]).length > LIMITE_MAXIMO) {
        return reply.code(400).send({ error: `maximo ${LIMITE_MAXIMO} elementos por lote` });
      }
      if (hayElementos && !(entradas as unknown[]).every(entradaValida)) {
        return reply.code(400).send({ error: 'entrada de elemento invalida' });
      }
      if (hayEtiquetas && (!Array.isArray(entradasEtiquetas) || entradasEtiquetas.length === 0)) {
        return reply.code(400).send({ error: 'falta el lote de etiquetas' });
      }
      if (hayEtiquetas && (entradasEtiquetas as unknown[]).length > LIMITE_MAXIMO) {
        return reply.code(400).send({ error: `maximo ${LIMITE_MAXIMO} etiquetas por lote` });
      }
      if (hayEtiquetas && !(entradasEtiquetas as unknown[]).every(entradaEtiquetaValida)) {
        return reply.code(400).send({ error: 'entrada de etiqueta invalida' });
      }

      const resultado = hayElementos
        ? push(request.usuarioId!, entradas as ElementoEntrada[])
        : { elementos: [], rechazados: [] };
      const resultadoEtiquetas = hayEtiquetas
        ? pushEtiquetas(request.usuarioId!, entradasEtiquetas as EtiquetaEntrada[])
        : { etiquetasDefinidas: [], rechazadas: [] };

      return {
        elementos: resultado.elementos,
        rechazados: resultado.rechazados,
        etiquetasDefinidas: resultadoEtiquetas.etiquetasDefinidas,
        etiquetasRechazadas: resultadoEtiquetas.rechazadas,
      };
    },
  );
}
