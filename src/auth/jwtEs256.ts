import { createSign } from 'node:crypto';

// Firma un JWT ES256 minimo, solo con lo que hace falta para el client_secret que exige Sign in
// with Apple (Apple no da un client_secret fijo: hay que firmarlo nosotros con la clave privada
// ES256 descargada una vez del portal de Apple Developer). Nada de esto verifica JWTs ajenos -
// para eso (verificar id_token de terceros) NO se usa, ver la nota en oauth.ts.

function base64Url(datos: Buffer | string): string {
  return Buffer.from(datos).toString('base64url');
}

export function firmarJwtEs256(
  payload: Record<string, unknown>,
  claveKeyId: string,
  clavePrivadaPem: string,
): string {
  const cabecera = { alg: 'ES256', kid: claveKeyId };
  const cuerpo = `${base64Url(JSON.stringify(cabecera))}.${base64Url(JSON.stringify(payload))}`;

  // Los JWT ES256 usan la firma "raw" (r || s, IEEE P1363), no el formato DER que da Node por
  // defecto para ECDSA: hace falta pedir explicitamente dsaEncoding: 'ieee-p1363'.
  const firma = createSign('sha256')
    .update(cuerpo)
    .sign({ key: clavePrivadaPem, dsaEncoding: 'ieee-p1363' });

  return `${cuerpo}.${base64Url(firma)}`;
}
