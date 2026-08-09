import { config } from '../config';
import { firmarJwtEs256 } from './jwtEs256';

export type Proveedor = 'google' | 'apple';
// 'dev' solo la usa /auth/dev-login (login de desarrollo, ver rutas.ts): comparte el modelo de
// usuario/perfil, pero nunca pasa por urlAutorizacion ni por un intercambio de codigo real.
export type ProveedorIdentidad = Proveedor | 'dev';

export interface PerfilOAuth {
  proveedor: ProveedorIdentidad;
  idProveedor: string; // claim "sub": identificador estable del usuario en el proveedor
  email: string | null;
  emailVerificado: boolean;
}

// Decodifica el PAYLOAD de un id_token (JWT) SIN verificar la firma. Es seguro hacerlo aqui,
// y solo aqui, porque este id_token no llega desde el navegador del usuario ni de ningun sitio
// no confiable: lo estamos leyendo de la respuesta HTTPS directa del endpoint /token del propio
// Google o Apple, a la que llegamos presentando nuestro client_secret (ver intercambiarCodigo*
// mas abajo). El canal ya es de confianza (TLS + nuestro secreto de cliente), asi que no hace
// falta anadir una libreria de verificacion JWT/JWKS solo para esto. Si alguna vez se aceptara un
// id_token directamente desde un cliente (sin pasar por este intercambio servidor-a-servidor), SI
// haria falta verificar la firma contra las claves publicas del proveedor.
function decodificarPayloadIdToken(idToken: string): Record<string, unknown> {
  const partes = idToken.split('.');
  if (partes.length !== 3) {
    throw new Error('id_token con formato invalido');
  }
  const json = Buffer.from(partes[1], 'base64url').toString('utf8');
  return JSON.parse(json) as Record<string, unknown>;
}

function perfilDesdeIdToken(proveedor: Proveedor, idToken: string): PerfilOAuth {
  const payload = decodificarPayloadIdToken(idToken);
  const sub = payload.sub;
  if (typeof sub !== 'string' || sub.length === 0) {
    throw new Error('id_token sin "sub"');
  }
  const email = typeof payload.email === 'string' ? payload.email.toLowerCase() : null;
  const emailVerificado = payload.email_verified === true || payload.email_verified === 'true';
  return { proveedor, idProveedor: sub, email, emailVerificado };
}

interface RespuestaToken {
  id_token?: string;
  error?: string;
  error_description?: string;
}

async function pedirToken(url: string, cuerpo: URLSearchParams): Promise<string> {
  const respuesta = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: cuerpo,
  });
  const datos = (await respuesta.json()) as RespuestaToken;
  if (!respuesta.ok || !datos.id_token) {
    throw new Error(
      `el proveedor rechazo el codigo: ${datos.error ?? respuesta.status} ${datos.error_description ?? ''}`.trim(),
    );
  }
  return datos.id_token;
}

export async function intercambiarCodigoGoogle(
  codigo: string,
  redirectUri: string,
): Promise<PerfilOAuth> {
  const cuerpo = new URLSearchParams({
    code: codigo,
    client_id: config.google.clientId ?? '',
    client_secret: config.google.clientSecret ?? '',
    redirect_uri: redirectUri,
    grant_type: 'authorization_code',
  });
  const idToken = await pedirToken('https://oauth2.googleapis.com/token', cuerpo);
  return perfilDesdeIdToken('google', idToken);
}

// Apple no da un client_secret fijo: hay que generar un JWT firmado con la clave privada ES256
// del portal de Apple Developer, valido como mucho 6 meses (aqui se regenera en cada peticion,
// con una vida de unos minutos, mas simple que cachearlo).
function generarClientSecretApple(ahora: () => number = () => Date.now()): string {
  const iat = Math.floor(ahora() / 1000);
  return firmarJwtEs256(
    {
      iss: config.apple.teamId,
      iat,
      exp: iat + 5 * 60,
      aud: 'https://appleid.apple.com',
      sub: config.apple.clientId,
    },
    config.apple.keyId ?? '',
    config.apple.privateKey ?? '',
  );
}

export async function intercambiarCodigoApple(
  codigo: string,
  redirectUri: string,
): Promise<PerfilOAuth> {
  const cuerpo = new URLSearchParams({
    code: codigo,
    client_id: config.apple.clientId ?? '',
    client_secret: generarClientSecretApple(),
    redirect_uri: redirectUri,
    grant_type: 'authorization_code',
  });
  const idToken = await pedirToken('https://appleid.apple.com/auth/token', cuerpo);
  return perfilDesdeIdToken('apple', idToken);
}

export function urlAutorizacion(
  proveedor: Proveedor,
  redirectUri: string,
  estado: string,
): string {
  if (proveedor === 'google') {
    const params = new URLSearchParams({
      client_id: config.google.clientId ?? '',
      redirect_uri: redirectUri,
      response_type: 'code',
      scope: 'openid email',
      state: estado,
      prompt: 'select_account',
    });
    return `https://accounts.google.com/o/oauth2/v2/auth?${params}`;
  }

  // Apple exige response_mode=form_post en cuanto se piden scopes (email): el callback llega por
  // POST con cuerpo application/x-www-form-urlencoded, no por GET con query string como Google.
  const params = new URLSearchParams({
    client_id: config.apple.clientId ?? '',
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'email',
    state: estado,
    response_mode: 'form_post',
  });
  return `https://appleid.apple.com/auth/authorize?${params}`;
}

export { decodificarPayloadIdToken, perfilDesdeIdToken, generarClientSecretApple };
