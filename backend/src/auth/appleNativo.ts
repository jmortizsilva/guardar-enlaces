// Comprobacion del token que devuelve Apple cuando el inicio de sesion se hace desde la propia
// app de iOS, sin pasar por el navegador (ver CONTRATO-API.md, POST /auth/apple-nativo).
//
// Aqui no se confia en nada de lo que manda el cliente: el token viene firmado por Apple y hay
// que verificar la firma con sus claves publicas antes de mirar su contenido. Cualquiera puede
// enviar un JSON con el "sub" que quiera.
//
// Sin librerias de JWT: Node sabe importar una clave en formato JWK y verificar RS256.

import { createHash, createPublicKey, verify, type JsonWebKey } from 'node:crypto';

const URL_CLAVES_APPLE = 'https://appleid.apple.com/auth/keys';
const EMISOR_APPLE = 'https://appleid.apple.com';
// Las claves de Apple cambian muy de vez en cuando; se guardan un rato para no pedirlas en cada
// inicio de sesion, pero no para siempre: si Apple rota una clave, hay que enterarse.
const DURACION_CACHE_MS = 60 * 60 * 1000;

export interface ClavePublicaApple {
  kty: string;
  kid: string;
  alg: string;
  n: string;
  e: string;
}

export interface IdentidadDeApple {
  /** El identificador estable del usuario para esta app. Es la identidad, no el correo. */
  sub: string;
  /** Apple solo lo manda la primera vez, y puede ser una direccion de reenvio privada. */
  email?: string;
}

export type ResultadoVerificacion =
  | { valido: true; identidad: IdentidadDeApple }
  | { valido: false; motivo: string };

interface CabeceraJwt {
  kid?: string;
  alg?: string;
}

interface CuerpoJwt {
  iss?: string;
  aud?: string | string[];
  sub?: string;
  exp?: number;
  email?: string;
  nonce?: string;
}

let cacheClaves: { claves: ClavePublicaApple[]; caducaEn: number } | null = null;

/** Las claves publicas de Apple, guardadas un rato para no pedirlas en cada inicio de sesion. */
export async function obtenerClavesDeApple(
  ahora: () => number = () => Date.now(),
): Promise<ClavePublicaApple[]> {
  if (cacheClaves && cacheClaves.caducaEn > ahora()) {
    return cacheClaves.claves;
  }
  const respuesta = await fetch(URL_CLAVES_APPLE);
  if (!respuesta.ok) {
    throw new Error(`Apple respondio ${respuesta.status} al pedirle sus claves`);
  }
  const datos = (await respuesta.json()) as { keys: ClavePublicaApple[] };
  cacheClaves = { claves: datos.keys, caducaEn: ahora() + DURACION_CACHE_MS };
  return datos.keys;
}

/** Solo para las pruebas: olvida las claves guardadas. */
export function olvidarClavesDeApple(): void {
  cacheClaves = null;
}

function decodificarParte(parte: string): unknown {
  return JSON.parse(Buffer.from(parte, 'base64url').toString('utf8'));
}

export interface OpcionesVerificacion {
  /** Para quien tiene que estar emitido el token: el identificador de la app de iOS. */
  audienciasValidas: string[];
  ahora?: () => number;
  /** Inyectable para las pruebas, que traen sus propias claves en vez de pedirselas a Apple. */
  obtenerClaves?: () => Promise<ClavePublicaApple[]>;
}

/**
 * Comprueba el token y devuelve la identidad, o el motivo por el que no vale.
 *
 * Devuelve el motivo en vez de lanzar porque ninguno de estos casos es una averia del servidor:
 * son tokens que no sirven, y la ruta los contesta con un 400.
 */
export async function verificarTokenDeApple(
  identityToken: string,
  nonceEnClaro: string,
  opciones: OpcionesVerificacion,
): Promise<ResultadoVerificacion> {
  const ahora = opciones.ahora ?? (() => Date.now());
  const partes = identityToken.split('.');
  if (partes.length !== 3) {
    return { valido: false, motivo: 'el token no tiene forma de JWT' };
  }

  let cabecera: CabeceraJwt;
  let cuerpo: CuerpoJwt;
  try {
    cabecera = decodificarParte(partes[0]) as CabeceraJwt;
    cuerpo = decodificarParte(partes[1]) as CuerpoJwt;
  } catch {
    return { valido: false, motivo: 'el token no se puede leer' };
  }

  // Solo RS256: aceptar lo que diga la cabecera permitiria mandar alg "none" y colarse sin firma.
  if (cabecera.alg !== 'RS256' || !cabecera.kid) {
    return { valido: false, motivo: 'el token no viene firmado como Apple los firma' };
  }

  const claves = await (opciones.obtenerClaves ?? (() => obtenerClavesDeApple(ahora)))();
  const clave = claves.find((k) => k.kid === cabecera.kid);
  if (!clave) {
    return { valido: false, motivo: 'el token dice venir de una clave que Apple no publica' };
  }

  const firmaValida = verify(
    'RSA-SHA256',
    Buffer.from(`${partes[0]}.${partes[1]}`),
    createPublicKey({ key: clave as unknown as JsonWebKey, format: 'jwk' }),
    Buffer.from(partes[2], 'base64url'),
  );
  if (!firmaValida) {
    return { valido: false, motivo: 'la firma no es de Apple' };
  }

  if (cuerpo.iss !== EMISOR_APPLE) {
    return { valido: false, motivo: 'el token no lo emitio Apple' };
  }

  const audiencias = Array.isArray(cuerpo.aud) ? cuerpo.aud : [cuerpo.aud];
  if (!audiencias.some((aud) => aud && opciones.audienciasValidas.includes(aud))) {
    return { valido: false, motivo: 'el token es para otra aplicacion' };
  }

  if (typeof cuerpo.exp !== 'number' || cuerpo.exp * 1000 <= ahora()) {
    return { valido: false, motivo: 'el token ha caducado' };
  }

  // El nonce ata el token a ESTA peticion: la app le paso a Apple el resumen del valor que manda
  // aqui en claro. Sin esto, un token de otra sesion valdria para entrar.
  const resumenEsperado = createHash('sha256').update(nonceEnClaro).digest('hex');
  if (!cuerpo.nonce || cuerpo.nonce !== resumenEsperado) {
    return { valido: false, motivo: 'el token no corresponde a esta peticion' };
  }

  if (!cuerpo.sub) {
    return { valido: false, motivo: 'el token no identifica a nadie' };
  }

  return { valido: true, identidad: { sub: cuerpo.sub, email: cuerpo.email } };
}
