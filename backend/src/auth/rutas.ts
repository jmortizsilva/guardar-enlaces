import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { config } from '../config';
import {
  buscarLoginPendiente,
  crearLoginPendiente,
  marcarErrorLoginPendiente,
  ModoLogin,
  resolverLoginPendiente,
  consumirCodigoCanje,
} from './loginPendientes';
import { crearExigirSesion } from './middleware';
import {
  intercambiarCodigoApple,
  intercambiarCodigoGoogle,
  Proveedor,
  urlAutorizacion,
} from './oauth';
import { iniciarSesion, renovarSesion, revocarSesion, revocarTodasLasSesiones } from './sesiones';
import { obtenerOCrearUsuario, obtenerUsuarioPorId } from './usuarios';

const PROVEEDORES: Proveedor[] = ['google', 'apple'];
const MODOS: ModoLogin[] = ['deeplink', 'polling'];

function redirectUriPara(proveedor: string): string {
  return `${config.urlPublica}/auth/callback/${proveedor}`;
}

function paginaHtml(titulo: string, mensaje: string): string {
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${titulo}</title></head>
<body><h1>${titulo}</h1><p>${mensaje}</p></body></html>`;
}

interface QueryIniciar {
  proveedor?: string;
  modo?: string;
  estado?: string;
  esquema?: string;
}

interface QueryOCuerpoCallback {
  code?: string;
  state?: string;
}

interface QueryEstado {
  estado?: string;
}

interface CuerpoCanjear {
  codigoCanje?: unknown;
}

interface CuerpoTokenRefresco {
  tokenRefresco?: unknown;
}

export async function registrarRutasAuth(app: FastifyInstance): Promise<void> {
  const exigirSesion = crearExigirSesion(config.tokenSecreto ?? '');

  app.get<{ Querystring: QueryIniciar }>(
    '/auth/iniciar',
    { config: { rateLimit: { max: 20, timeWindow: '15 minutes' } } },
    async (request: FastifyRequest<{ Querystring: QueryIniciar }>, reply: FastifyReply) => {
      const { proveedor, estado } = request.query;
      const modo = request.query.modo as ModoLogin | undefined;
      const esquema = request.query.esquema;

      if (!proveedor || !PROVEEDORES.includes(proveedor as Proveedor)) {
        return reply.code(400).send({ error: 'proveedor no soportado' });
      }
      if (!modo || !MODOS.includes(modo)) {
        return reply.code(400).send({ error: 'modo no soportado' });
      }
      if (!estado) {
        return reply.code(400).send({ error: 'falta "estado"' });
      }
      if (modo === 'deeplink' && !esquema) {
        return reply.code(400).send({ error: 'el modo deeplink exige "esquema"' });
      }

      // "esquema" es solo el nombre (ej. "guardarenlaces", como el campo `scheme` de app.json en
      // Expo), sin "://": el "://auth-callback" se anade al construir el redirect final. Se
      // acepta tambien si el cliente manda el "://" de mas, para no depender de que lo recuerde.
      const esquemaLimpio = esquema ? esquema.replace(/:\/\/?$/, '') : null;
      crearLoginPendiente(estado, modo, esquemaLimpio);
      const url = urlAutorizacion(proveedor as Proveedor, redirectUriPara(proveedor), estado);
      return reply.redirect(url);
    },
  );

  async function manejarCallback(
    proveedorTexto: string,
    datos: QueryOCuerpoCallback,
    request: FastifyRequest,
    reply: FastifyReply,
  ): Promise<FastifyReply> {
    reply.type('text/html; charset=utf-8');

    if (!PROVEEDORES.includes(proveedorTexto as Proveedor)) {
      return reply.code(400).send(paginaHtml('Error', 'Proveedor no soportado.'));
    }
    const proveedor = proveedorTexto as Proveedor;
    const { code, state } = datos;
    if (!code || !state) {
      return reply.code(400).send(paginaHtml('Error', 'Falta el codigo o el estado.'));
    }

    const pendiente = buscarLoginPendiente(state);
    if (!pendiente) {
      return reply
        .code(400)
        .send(
          paginaHtml(
            'Enlace caducado',
            'Vuelve a intentar el inicio de sesion desde la aplicacion.',
          ),
        );
    }

    let codigoCanje: string | undefined;
    let error: string | undefined;
    try {
      const perfil =
        proveedor === 'google'
          ? await intercambiarCodigoGoogle(code, redirectUriPara(proveedor))
          : await intercambiarCodigoApple(code, redirectUriPara(proveedor));
      const usuario = obtenerOCrearUsuario(perfil);
      if (!usuario) {
        error = 'sin_invitacion';
      } else {
        codigoCanje = resolverLoginPendiente(state, usuario.id);
      }
    } catch (err) {
      request.log.warn({ err }, 'fallo el intercambio OAuth');
      error = 'fallo_intercambio';
    }
    if (error) {
      marcarErrorLoginPendiente(state, error);
    }

    if (pendiente.modo === 'deeplink' && pendiente.esquema) {
      const destino = codigoCanje
        ? `${pendiente.esquema}://auth-callback?codigo=${encodeURIComponent(codigoCanje)}`
        : `${pendiente.esquema}://auth-callback?error=${encodeURIComponent(error ?? 'desconocido')}`;
      return reply.redirect(destino);
    }

    return reply.send(
      codigoCanje
        ? paginaHtml('Sesion iniciada', 'Ya puedes volver a la aplicacion.')
        : paginaHtml(
            'No se pudo iniciar sesion',
            error === 'sin_invitacion'
              ? 'Tu cuenta no tiene acceso. Pide que te inviten.'
              : 'Ha fallado el inicio de sesion, vuelve a intentarlo desde la aplicacion.',
          ),
    );
  }

  app.get<{ Params: { proveedor: string }; Querystring: QueryOCuerpoCallback }>(
    '/auth/callback/:proveedor',
    async (request, reply) =>
      manejarCallback(request.params.proveedor, request.query, request, reply),
  );

  // Apple manda el callback por POST (response_mode=form_post) cuando se pide el scope "email".
  app.post<{ Params: { proveedor: string }; Body: QueryOCuerpoCallback }>(
    '/auth/callback/:proveedor',
    async (request, reply) =>
      manejarCallback(request.params.proveedor, request.body, request, reply),
  );

  app.get<{ Querystring: QueryEstado }>('/auth/estado', async (request, reply) => {
    const { estado } = request.query;
    if (!estado) {
      return reply.code(400).send({ error: 'falta "estado"' });
    }
    const pendiente = buscarLoginPendiente(estado);
    if (!pendiente) {
      return reply.code(404).send({ error: 'estado desconocido o caducado' });
    }
    if (pendiente.error) {
      return { listo: true, error: pendiente.error };
    }
    if (pendiente.codigo_canje) {
      return { listo: true, codigoCanje: pendiente.codigo_canje };
    }
    return { listo: false };
  });

  app.post<{ Body: CuerpoCanjear }>(
    '/auth/canjear',
    { config: { rateLimit: { max: 20, timeWindow: '15 minutes' } } },
    async (request, reply) => {
      const codigoCanje = request.body?.codigoCanje;
      if (typeof codigoCanje !== 'string' || codigoCanje.length === 0) {
        return reply.code(400).send({ error: 'falta "codigoCanje"' });
      }
      const usuarioId = consumirCodigoCanje(codigoCanje);
      if (usuarioId === undefined) {
        return reply.code(400).send({ error: 'codigo invalido o caducado' });
      }
      const usuario = obtenerUsuarioPorId(usuarioId);
      const tokens = iniciarSesion(usuarioId, null);
      return { ...tokens, usuario };
    },
  );

  app.post<{ Body: CuerpoTokenRefresco }>('/auth/renovar', async (request, reply) => {
    const tokenRefresco = request.body?.tokenRefresco;
    if (typeof tokenRefresco !== 'string' || tokenRefresco.length === 0) {
      return reply.code(400).send({ error: 'falta "tokenRefresco"' });
    }
    const tokens = renovarSesion(tokenRefresco);
    if (!tokens) {
      return reply.code(401).send({ error: 'token de refresco invalido o caducado' });
    }
    return tokens;
  });

  app.post<{ Body: CuerpoTokenRefresco }>('/auth/logout', async (request) => {
    const tokenRefresco = request.body?.tokenRefresco;
    if (typeof tokenRefresco === 'string' && tokenRefresco.length > 0) {
      revocarSesion(tokenRefresco);
    }
    return { ok: true };
  });

  app.post('/auth/logout-todas', { preHandler: exigirSesion }, async (request) => {
    revocarTodasLasSesiones(request.usuarioId!);
    return { ok: true };
  });

  // SOLO DESARROLLO: la ruta ni siquiera se registra salvo que PERMITIR_LOGIN_DEV=true, para que
  // no pueda colarse en un despliegue real por descuido. Crea/reutiliza el usuario (respetando la
  // lista de invitados, igual que el flujo real) y devuelve tokens sin pasar por Google/Apple.
  // Pensado para construir y probar los clientes antes de tener credenciales OAuth reales.
  if (config.permitirLoginDev) {
    app.post<{ Body: { email?: unknown } }>('/auth/dev-login', async (request, reply) => {
      const email = request.body?.email;
      if (typeof email !== 'string' || !email.includes('@')) {
        return reply.code(400).send({ error: 'falta "email"' });
      }
      const usuario = obtenerOCrearUsuario({
        proveedor: 'dev',
        idProveedor: email.toLowerCase(),
        email: email.toLowerCase(),
        emailVerificado: true,
      });
      if (!usuario) {
        return reply.code(403).send({ error: 'ese correo no esta invitado' });
      }
      const tokens = iniciarSesion(usuario.id, 'dev-login');
      return { ...tokens, usuario };
    });
  }
}
