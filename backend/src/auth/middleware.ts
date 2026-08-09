import { FastifyReply, FastifyRequest } from 'fastify';
import { verificarTokenAcceso } from './tokenAcceso';

declare module 'fastify' {
  interface FastifyRequest {
    usuarioId?: number;
  }
}

// preHandler comun a todas las rutas protegidas: exige "Authorization: Bearer <tokenAcceso>" y
// adjunta el usuarioId a la request. Las rutas de /auth/ (login, callback, canjear, renovar) no lo
// usan porque todavia no hay sesion.
export function crearExigirSesion(secreto: string) {
  return async function exigirSesion(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const cabecera = request.headers.authorization;
    const token = cabecera?.startsWith('Bearer ') ? cabecera.slice('Bearer '.length) : undefined;
    const usuarioId = token ? verificarTokenAcceso(token, secreto) : undefined;
    if (usuarioId === undefined) {
      reply.code(401).send({ error: 'sesion invalida o caducada' });
      return;
    }
    request.usuarioId = usuarioId;
  };
}
