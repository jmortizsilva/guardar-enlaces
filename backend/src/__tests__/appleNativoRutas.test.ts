import { createHash, createSign, generateKeyPairSync, type JsonWebKey } from 'node:crypto';
import Fastify, { FastifyInstance } from 'fastify';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const SECRETO = 'secreto-de-prueba';
const APP_IOS = 'com.jmortizsilva.guardarenlaces';
const NONCE = 'aleatorio-de-esta-peticion';

// Un Apple de mentira: la prueba firma los tokens y sirve la clave publica en lugar de la suya.
const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const claveDeApple = {
  ...(publicKey.export({ format: 'jwk' }) as JsonWebKey),
  kty: 'RSA',
  kid: 'clave-de-apple',
  alg: 'RS256',
};

function tokenDeApple(cambios: Record<string, unknown> = {}): string {
  const cabecera = Buffer.from(
    JSON.stringify({ alg: 'RS256', kid: 'clave-de-apple' }),
  ).toString('base64url');
  const cuerpo = Buffer.from(
    JSON.stringify({
      iss: 'https://appleid.apple.com',
      aud: APP_IOS,
      sub: 'apple-sub-1',
      email: 'persona@privaterelay.appleid.com',
      exp: Math.floor(Date.now() / 1000) + 600,
      nonce: createHash('sha256').update(NONCE).digest('hex'),
      ...cambios,
    }),
  ).toString('base64url');
  const firma = createSign('RSA-SHA256').update(`${cabecera}.${cuerpo}`).sign(privateKey);
  return `${cabecera}.${cuerpo}.${firma.toString('base64url')}`;
}

let app: FastifyInstance;
let inicializarBd: (typeof import('../db'))['inicializarBd'];
let olvidarClavesDeApple: (typeof import('../auth/appleNativo'))['olvidarClavesDeApple'];

beforeAll(async () => {
  process.env.ENLACES_TOKEN_SECRET = SECRETO;
  process.env.URL_PUBLICA = 'https://servidor.ejemplo.com';
  process.env.APPLE_APP_ID = APP_IOS;

  ({ inicializarBd } = await import('../db'));
  ({ olvidarClavesDeApple } = await import('../auth/appleNativo'));
  const { registrarRutasAuth } = await import('../auth/rutas');

  app = Fastify();
  await app.register(registrarRutasAuth);
});

beforeEach(() => {
  inicializarBd(':memory:');
  olvidarClavesDeApple();
  // Las claves publicas se piden a Apple por HTTP: aqui las sirve la prueba.
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify({ keys: [claveDeApple] }))),
  );
});

async function entrarCon(cuerpo: Record<string, unknown>) {
  return app.inject({ method: 'POST', url: '/auth/apple-nativo', payload: cuerpo });
}

describe('POST /auth/apple-nativo', () => {
  it('con un token bueno da sesion y crea la cuenta', async () => {
    const res = await entrarCon({ identityToken: tokenDeApple(), nonce: NONCE });

    expect(res.statusCode).toBe(200);
    const datos = res.json();
    expect(datos.tokenAcceso).toBeTruthy();
    expect(datos.tokenRefresco).toBeTruthy();
    expect(datos.usuario).toMatchObject({
      email: 'persona@privaterelay.appleid.com',
      proveedor: 'apple',
    });
  });

  it('entrar dos veces no crea dos cuentas', async () => {
    const primera = await entrarCon({ identityToken: tokenDeApple(), nonce: NONCE });
    const segunda = await entrarCon({ identityToken: tokenDeApple(), nonce: NONCE });

    expect(segunda.statusCode).toBe(200);
    expect(segunda.json().usuario.id).toBe(primera.json().usuario.id);
  });

  it('la segunda vez Apple ya no manda correo, y da igual', async () => {
    await entrarCon({ identityToken: tokenDeApple(), nonce: NONCE });

    const res = await entrarCon({ identityToken: tokenDeApple({ email: undefined }), nonce: NONCE });

    expect(res.statusCode).toBe(200);
    expect(res.json().usuario.email).toBe('persona@privaterelay.appleid.com');
  });

  it('sin correo la primera vez no hay cuenta que crear', async () => {
    const res = await entrarCon({
      identityToken: tokenDeApple({ email: undefined, sub: 'otro-sub' }),
      nonce: NONCE,
    });

    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe('sin_email');
  });

  it('un token de otra peticion no sirve', async () => {
    const res = await entrarCon({ identityToken: tokenDeApple(), nonce: 'otro-nonce' });

    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe('el token no corresponde a esta peticion');
  });

  it('un token para otra aplicacion no sirve', async () => {
    const res = await entrarCon({ identityToken: tokenDeApple({ aud: 'com.otra.app' }), nonce: NONCE });

    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe('el token es para otra aplicacion');
  });

  it('faltando el token o el nonce, lo dice', async () => {
    expect((await entrarCon({ nonce: NONCE })).json().error).toBe('falta "identityToken"');
    expect((await entrarCon({ identityToken: tokenDeApple() })).json().error).toBe('falta "nonce"');
  });
});
