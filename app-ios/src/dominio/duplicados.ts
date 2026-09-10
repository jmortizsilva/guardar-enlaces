/**
 * Detectar si una URL ya esta guardada. Logica pura, sin red ni almacen.
 *
 * Comparar las URLs tal cual no sirve: la misma pagina llega con "www" o sin
 * el, con http o https, con una barra final de mas, con un ancla, o arrastrando
 * los parametros de seguimiento que anaden las redes sociales y los boletines.
 * Todo eso es el mismo enlace para una persona, que es quien va a oir el aviso.
 *
 * Lo que NO se toca: mayusculas y minusculas de la ruta (hay servidores donde
 * distinguen), ni el orden de los parametros que si importan (se ordenan para
 * comparar, pero no se descartan).
 *
 * Es duplicado solo dentro de la biblioteca de cada uno: que otra persona tenga
 * guardado el mismo enlace no pinta nada aqui, sus elementos ni se ven.
 */
import { Elemento } from './elemento';

// Parametros que solo sirven para saber de donde vino la visita.
const PREFIJOS_DE_SEGUIMIENTO = ['utm_'];
const PARAMETROS_DE_SEGUIMIENTO = new Set([
  'fbclid',
  'gclid',
  'igshid',
  'mc_cid',
  'mc_eid',
  'ref',
  'ref_src',
  'si',
]);

function esDeSeguimiento(clave: string): boolean {
  const minuscula = clave.toLowerCase();
  return (
    PARAMETROS_DE_SEGUIMIENTO.has(minuscula) ||
    PREFIJOS_DE_SEGUIMIENTO.some((prefijo) => minuscula.startsWith(prefijo))
  );
}

/**
 * Forma canonica para comparar, no para guardar ni para abrir: se queda sin
 * esquema a proposito, porque http y https son la misma pagina para esto.
 *
 * Si la URL no se puede analizar, se devuelve tal cual en minusculas: mejor no
 * detectar un duplicado que inventarse uno.
 */
export function normalizarUrl(url: string): string {
  const limpia = url.trim();
  try {
    const partes = new URL(limpia);
    // OJO: el URL de React Native no lanza con una cadena que no sea una URL
    // (el de Node si), simplemente devuelve hostname vacio. Sin esta guarda,
    // en el telefono TODO lo que no fuera una URL normalizaria igual y se
    // tomaria por duplicado de lo anterior. Ver sesion/loginProveedor.ts.
    if (!partes.hostname) {
      return limpia.toLowerCase();
    }
    const host = partes.hostname.toLowerCase().replace(/^www\./, '');
    const ruta = partes.pathname.replace(/\/+$/, '');
    const parametros = Array.from(partes.searchParams.entries())
      .filter(([clave]) => !esDeSeguimiento(clave))
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([clave, valor]) => `${clave}=${valor}`)
      .join('&');
    return `${host}${ruta}${parametros ? `?${parametros}` : ''}`;
  } catch {
    return limpia.toLowerCase();
  }
}

export function mismaUrl(una: string, otra: string): boolean {
  return normalizarUrl(una) === normalizarUrl(otra);
}

/**
 * El elemento ya guardado con esa misma URL, si lo hay. Los borrados no
 * cuentan: si lo tiraste, volver a guardarlo es un alta normal.
 */
export function buscarDuplicado(elementos: readonly Elemento[], url: string): Elemento | undefined {
  const buscada = normalizarUrl(url);
  return elementos.find((elemento) => !elemento.borrado && normalizarUrl(elemento.url) === buscada);
}
