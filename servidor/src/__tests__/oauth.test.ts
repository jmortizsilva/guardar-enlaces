import { beforeAll, describe, expect, it } from 'vitest';

function fakeIdToken(payload: Record<string, unknown>): string {
  const cabecera = Buffer.from(JSON.stringify({ alg: 'RS256' })).toString('base64url');
  const cuerpo = Buffer.from(JSON.stringify(payload)).toString('base64url');
  return `${cabecera}.${cuerpo}.firma-no-verificada-en-este-punto`;
}

let decodificarPayloadIdToken: (typeof import('../auth/oauth'))['decodificarPayloadIdToken'];
let perfilDesdeIdToken: (typeof import('../auth/oauth'))['perfilDesdeIdToken'];
let urlAutorizacion: (typeof import('../auth/oauth'))['urlAutorizacion'];

beforeAll(async () => {
  process.env.GOOGLE_CLIENT_ID = 'google-client-id';
  process.env.APPLE_CLIENT_ID = 'com.ejemplo.servicios';
  const mod = await import('../auth/oauth');
  ({ decodificarPayloadIdToken, perfilDesdeIdToken, urlAutorizacion } = mod);
});

describe('decodificarPayloadIdToken', () => {
  it('decodifica el payload sin verificar la firma', () => {
    const jwt = fakeIdToken({ sub: 'abc123', email: 'A@B.com' });
    expect(decodificarPayloadIdToken(jwt)).toEqual({ sub: 'abc123', email: 'A@B.com' });
  });

  it('rechaza un token con menos de 3 partes', () => {
    expect(() => decodificarPayloadIdToken('solo.dospartes')).toThrow();
  });
});

describe('perfilDesdeIdToken', () => {
  it('extrae sub/email y normaliza el email a minusculas', () => {
    const jwt = fakeIdToken({ sub: 'sub1', email: 'Persona@Ejemplo.COM', email_verified: true });
    expect(perfilDesdeIdToken('google', jwt)).toEqual({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });
  });

  it('Apple manda email_verified como texto "true", no booleano', () => {
    const jwt = fakeIdToken({ sub: 'sub2', email: 'x@y.com', email_verified: 'true' });
    expect(perfilDesdeIdToken('apple', jwt).emailVerificado).toBe(true);
  });

  it('sin sub, lanza', () => {
    const jwt = fakeIdToken({ email: 'x@y.com' });
    expect(() => perfilDesdeIdToken('google', jwt)).toThrow();
  });

  it('sin email, el perfil lo deja en null en vez de fallar', () => {
    const jwt = fakeIdToken({ sub: 'sub3' });
    expect(perfilDesdeIdToken('google', jwt).email).toBeNull();
  });
});

describe('urlAutorizacion', () => {
  it('Google: response_type=code y sin response_mode', () => {
    const url = new URL(urlAutorizacion('google', 'https://x.com/cb', 'estado1'));
    expect(url.hostname).toBe('accounts.google.com');
    expect(url.searchParams.get('client_id')).toBe('google-client-id');
    expect(url.searchParams.get('redirect_uri')).toBe('https://x.com/cb');
    expect(url.searchParams.get('state')).toBe('estado1');
    expect(url.searchParams.get('response_mode')).toBeNull();
  });

  it('Apple: exige response_mode=form_post', () => {
    const url = new URL(urlAutorizacion('apple', 'https://x.com/cb', 'estado2'));
    expect(url.hostname).toBe('appleid.apple.com');
    expect(url.searchParams.get('response_mode')).toBe('form_post');
    expect(url.searchParams.get('scope')).toContain('email');
  });
});
