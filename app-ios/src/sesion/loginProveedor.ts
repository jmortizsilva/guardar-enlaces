/**
 * Login con proveedor (Google/Apple) segun backend/docs/CONTRATO-API.md.
 *
 * La app NUNCA habla con Google: abre /auth/iniciar del backend en una
 * ASWebAuthenticationSession, el backend hace todo el intercambio OAuth y
 * devuelve el navegador a guardarenlaces://auth-callback?codigo=... El codigo
 * de canje es de un solo uso y dura ~60s: se cambia por tokens en /auth/canjear
 * (eso lo hace Sesion, aqui solo se consigue el codigo).
 */
import * as Crypto from 'expo-crypto';
import * as WebBrowser from 'expo-web-browser';

/** Debe coincidir con "scheme" en app.json: es lo que registra el esquema en iOS. */
export const ESQUEMA = 'guardarenlaces';
export const URL_CALLBACK = `${ESQUEMA}://auth-callback`;

export type Proveedor = 'google' | 'apple';

export type ResultadoLogin =
  | { estado: 'exito'; codigoCanje: string }
  /** El usuario cerro la hoja de Safari o rechazo el permiso: no es un error. */
  | { estado: 'cancelado' }
  | { estado: 'error'; mensaje: string };

/**
 * El "estado" del contrato: cadena opaca de un solo uso que ata la vuelta del
 * callback a esta peticion concreta. 16 bytes en hexadecimal.
 */
export async function generarEstado(
  aleatorio: (n: number) => Promise<Uint8Array> = Crypto.getRandomBytesAsync,
): Promise<string> {
  const bytes = await aleatorio(16);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

/** Traduce los motivos de error del contrato a algo que se pueda leer en voz alta. */
export function mensajeDeError(motivo: string): string {
  if (motivo === 'sin_email') {
    return 'Tu cuenta no ha dado ningún correo, y hace falta para crear la cuenta.';
  }
  if (motivo === 'fallo_intercambio') {
    return 'Google rechazó el inicio de sesión. Vuelve a intentarlo.';
  }
  return 'No se pudo iniciar sesión.';
}

/**
 * Lee la URL de vuelta del callback. Pura a proposito: es la unica parte con
 * casuistica real y asi se prueba sin navegador.
 */
export function leerCallback(url: string): ResultadoLogin {
  // OJO: el URL global de React Native (Libraries/Blob/URL.js, inyectado en
  // setUpXHR.js) no es un parser de verdad, sino unas expresiones regulares, y
  // casi todas sus partes (host, pathname, origin) solo reconocen http/https:
  // con un esquema propio devuelven cadena vacia. Lo que si funciona es
  // `search`, que busca el "?" en crudo, y de ahi searchParams. Por eso aqui se
  // leen solo los parametros. En jest esto no se nota (ahi el URL global es el
  // de Node, completo): la prueba pasaria igual con codigo que fallara en el
  // telefono.
  const parametros = new URL(url).searchParams;
  const codigo = parametros.get('codigo');
  if (codigo) {
    return { estado: 'exito', codigoCanje: codigo };
  }
  return { estado: 'error', mensaje: mensajeDeError(parametros.get('error') ?? '') };
}

/**
 * Abre la hoja de autenticacion del sistema y espera la vuelta.
 *
 * `openAuthSessionAsync` usa ASWebAuthenticationSession, que captura el
 * redirect a URL_CALLBACK dentro de la propia hoja: la vuelta NO pasa por el
 * manejador de deep links de la app (+native-intent.ts no ve nada de esto).
 *
 * Sin `preferEphemeralSession` a proposito (por defecto es false): asi la hoja
 * comparte las cookies de Safari y, si ya hay sesion de Google en el telefono,
 * basta con confirmar la cuenta en vez de teclear correo y contrasena.
 */
export async function pedirCodigoCanje(
  urlAutorizacion: string,
  abrirAuth: typeof WebBrowser.openAuthSessionAsync = WebBrowser.openAuthSessionAsync,
): Promise<ResultadoLogin> {
  const resultado = await abrirAuth(urlAutorizacion, URL_CALLBACK);
  if (resultado.type !== 'success') {
    return { estado: 'cancelado' };
  }
  return leerCallback(resultado.url);
}
