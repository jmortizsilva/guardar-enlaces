import { createHmac, timingSafeEqual } from 'node:crypto';

// Token de acceso SIN ESTADO: "usuarioId.expiraEnMs.firma". No se guarda en base de datos (a
// diferencia del token de refresco, que si es revocable) para no pagar una consulta a SQLite en
// cada peticion de sincronizacion. Corta duracion (ver DURACION_ACCESO_MS en sesiones.ts).

function firmar(cuerpo: string, secreto: string): string {
  return createHmac('sha256', secreto).update(cuerpo).digest('hex');
}

export function emitirTokenAcceso(
  usuarioId: number,
  expiraEn: number,
  secreto: string,
): string {
  const cuerpo = `${usuarioId}.${expiraEn}`;
  return `${cuerpo}.${firmar(cuerpo, secreto)}`;
}

// Devuelve el usuarioId si el token es valido y no ha expirado; undefined en cualquier otro caso
// (formato invalido, firma incorrecta, caducado). No distingue el motivo a proposito.
export function verificarTokenAcceso(
  token: string,
  secreto: string,
  ahora: () => number = () => Date.now(),
): number | undefined {
  const partes = token.split('.');
  if (partes.length !== 3) {
    return undefined;
  }
  const [usuarioIdTexto, expiraEnTexto, firmaRecibida] = partes;
  const cuerpo = `${usuarioIdTexto}.${expiraEnTexto}`;
  const firmaEsperada = firmar(cuerpo, secreto);

  const bufRecibida = Buffer.from(firmaRecibida, 'hex');
  const bufEsperada = Buffer.from(firmaEsperada, 'hex');
  if (bufRecibida.length !== bufEsperada.length || !timingSafeEqual(bufRecibida, bufEsperada)) {
    return undefined;
  }

  const expiraEn = Number(expiraEnTexto);
  const usuarioId = Number(usuarioIdTexto);
  if (!Number.isFinite(expiraEn) || !Number.isFinite(usuarioId) || expiraEn <= ahora()) {
    return undefined;
  }
  return usuarioId;
}
