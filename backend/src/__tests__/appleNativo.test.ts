import { createHash, createSign, generateKeyPairSync, type JsonWebKey } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { verificarTokenDeApple, type ClavePublicaApple } from '../auth/appleNativo';

// Un Apple de mentira: su par de claves, para poder firmar tokens en la prueba y comprobar que
// solo se acepta lo que firma el, y no cualquiera.
const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const claveDeApple: ClavePublicaApple = {
  ...(publicKey.export({ format: 'jwk' }) as JsonWebKey & { n: string; e: string }),
  kty: 'RSA',
  kid: 'clave-de-apple',
  alg: 'RS256',
};

const APP = 'com.jmortizsilva.guardarenlaces';
const AHORA = 1_800_000_000_000;
const NONCE = 'un-valor-aleatorio';

function resumenDe(nonce: string): string {
  return createHash('sha256').update(nonce).digest('hex');
}

function base64url(objeto: unknown): string {
  return Buffer.from(JSON.stringify(objeto)).toString('base64url');
}

/** Un token como los que manda Apple, con lo que se le quiera cambiar. */
function tokenDeApple(
  cambios: Record<string, unknown> = {},
  cabeceraCambios: Record<string, unknown> = {},
  firmante = privateKey,
): string {
  const cabecera = base64url({ alg: 'RS256', kid: 'clave-de-apple', ...cabeceraCambios });
  const cuerpo = base64url({
    iss: 'https://appleid.apple.com',
    aud: APP,
    sub: '001234.abcdef.5678',
    email: 'persona@privaterelay.appleid.com',
    exp: Math.floor(AHORA / 1000) + 600,
    nonce: resumenDe(NONCE),
    ...cambios,
  });
  const firma = createSign('RSA-SHA256').update(`${cabecera}.${cuerpo}`).sign(firmante);
  return `${cabecera}.${cuerpo}.${firma.toString('base64url')}`;
}

const opciones = {
  audienciasValidas: [APP],
  ahora: () => AHORA,
  obtenerClaves: async () => [claveDeApple],
};

describe('verificarTokenDeApple', () => {
  it('acepta un token bien firmado y devuelve quien es', async () => {
    const resultado = await verificarTokenDeApple(tokenDeApple(), NONCE, opciones);

    expect(resultado).toEqual({
      valido: true,
      identidad: { sub: '001234.abcdef.5678', email: 'persona@privaterelay.appleid.com' },
    });
  });

  it('acepta que no venga correo: Apple solo lo manda la primera vez', async () => {
    const resultado = await verificarTokenDeApple(
      tokenDeApple({ email: undefined }),
      NONCE,
      opciones,
    );

    expect(resultado).toEqual({
      valido: true,
      identidad: { sub: '001234.abcdef.5678', email: undefined },
    });
  });

  it('rechaza un token firmado por otro', async () => {
    const otro = generateKeyPairSync('rsa', { modulusLength: 2048 });

    const resultado = await verificarTokenDeApple(
      tokenDeApple({}, {}, otro.privateKey),
      NONCE,
      opciones,
    );

    expect(resultado).toEqual({ valido: false, motivo: 'la firma no es de Apple' });
  });

  it('rechaza un token sin firmar aunque su cabecera diga que vale asi', async () => {
    // El ataque de siempre contra los JWT: poner alg "none" y que el servidor se lo crea.
    const cabecera = base64url({ alg: 'none', kid: 'clave-de-apple' });
    const cuerpo = base64url({
      iss: 'https://appleid.apple.com',
      aud: APP,
      sub: 'el-que-yo-quiera',
      exp: Math.floor(AHORA / 1000) + 600,
      nonce: resumenDe(NONCE),
    });

    const resultado = await verificarTokenDeApple(`${cabecera}.${cuerpo}.`, NONCE, opciones);

    expect(resultado.valido).toBe(false);
  });

  it('rechaza un token que no emitio Apple', async () => {
    const resultado = await verificarTokenDeApple(
      tokenDeApple({ iss: 'https://otro-sitio.example' }),
      NONCE,
      opciones,
    );

    expect(resultado).toEqual({ valido: false, motivo: 'el token no lo emitio Apple' });
  });

  it('rechaza un token emitido para otra aplicacion', async () => {
    const resultado = await verificarTokenDeApple(
      tokenDeApple({ aud: 'com.otra.app' }),
      NONCE,
      opciones,
    );

    expect(resultado).toEqual({ valido: false, motivo: 'el token es para otra aplicacion' });
  });

  it('rechaza un token caducado', async () => {
    const resultado = await verificarTokenDeApple(
      tokenDeApple({ exp: Math.floor(AHORA / 1000) - 1 }),
      NONCE,
      opciones,
    );

    expect(resultado).toEqual({ valido: false, motivo: 'el token ha caducado' });
  });

  it('rechaza un token que no corresponde a esta peticion', async () => {
    // Un token robado de otra sesion: bien firmado, sin caducar, pero con otro nonce.
    const resultado = await verificarTokenDeApple(tokenDeApple(), 'otro-nonce-distinto', opciones);

    expect(resultado).toEqual({ valido: false, motivo: 'el token no corresponde a esta peticion' });
  });

  it('rechaza un token de una clave que Apple no publica', async () => {
    const resultado = await verificarTokenDeApple(
      tokenDeApple({}, { kid: 'una-clave-inventada' }),
      NONCE,
      opciones,
    );

    expect(resultado).toEqual({
      valido: false,
      motivo: 'el token dice venir de una clave que Apple no publica',
    });
  });

  it('rechaza cualquier cosa que no sea un JWT', async () => {
    const resultado = await verificarTokenDeApple('esto no es un token', NONCE, opciones);

    expect(resultado).toEqual({ valido: false, motivo: 'el token no tiene forma de JWT' });
  });
});
