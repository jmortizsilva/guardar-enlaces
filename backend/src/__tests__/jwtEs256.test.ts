import { generateKeyPairSync, verify } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { firmarJwtEs256 } from '../auth/jwtEs256';

describe('firmarJwtEs256', () => {
  it('produce un JWT de 3 partes cuya firma verifica con la clave publica', () => {
    const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    const privadaPem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();

    const jwt = firmarJwtEs256({ iss: 'equipo123', sub: 'com.ejemplo.app' }, 'clave1', privadaPem);
    const partes = jwt.split('.');
    expect(partes).toHaveLength(3);

    const cabecera = JSON.parse(Buffer.from(partes[0], 'base64url').toString('utf8'));
    expect(cabecera).toEqual({ alg: 'ES256', kid: 'clave1' });

    const payload = JSON.parse(Buffer.from(partes[1], 'base64url').toString('utf8'));
    expect(payload).toEqual({ iss: 'equipo123', sub: 'com.ejemplo.app' });

    const cuerpoFirmado = `${partes[0]}.${partes[1]}`;
    const firma = Buffer.from(partes[2], 'base64url');
    const valida = verify(
      'sha256',
      Buffer.from(cuerpoFirmado),
      { key: publicKey, dsaEncoding: 'ieee-p1363' },
      firma,
    );
    expect(valida).toBe(true);
  });
});
