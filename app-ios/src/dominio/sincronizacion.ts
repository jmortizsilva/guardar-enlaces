/**
 * Fusion de la cache local con las respuestas de /sincronizar. Logica pura,
 * sin red (la llamada HTTP vive en src/api y src/sincronizador).
 */
import { Elemento } from './elemento';

export type Cache = Record<string, Elemento>;

/**
 * Fusiona el resultado de un GET /sincronizar en la cache local.
 *
 * El servidor ya resolvio los conflictos entre dispositivos, asi que en
 * principio basta con sobrescribir. La unica salvedad es no pisar un cambio
 * local que todavia esta en el outbox (pendiente de subir) y es MAS reciente
 * que lo que acaba de llegar del pull: si no, un pull que se solape con un
 * push en curso podria hacer "retroceder" visualmente un cambio que el
 * usuario acaba de hacer, hasta que el push confirme.
 */
export function aplicarPull(
  cache: Cache,
  recibidos: readonly Elemento[],
  pendientes: Cache,
): Cache {
  const nueva = { ...cache };
  for (const elemento of recibidos) {
    const pendiente = pendientes[elemento.id];
    if (pendiente !== undefined && pendiente.actualizadoEn > elemento.actualizadoEn) {
      continue;
    }
    nueva[elemento.id] = elemento;
  }
  return nueva;
}

/**
 * El POST /sincronizar devuelve la version DEFINITIVA de cada elemento del
 * lote (puede diferir de lo enviado si se perdio un conflicto): la cache
 * local se sustituye siempre por lo que responde el servidor.
 */
export function aplicarRespuestaPush(cache: Cache, definitivos: readonly Elemento[]): Cache {
  const nueva = { ...cache };
  for (const elemento of definitivos) {
    nueva[elemento.id] = elemento;
  }
  return nueva;
}

/** Los que no estan borrados, mas recientes primero. */
export function elementosVisibles(cache: Cache): Elemento[] {
  return Object.values(cache)
    .filter((e) => !e.borrado)
    .sort((a, b) => b.actualizadoEn - a.actualizadoEn);
}

/** Filtro local sobre titulo, url y etiquetas (sin distinguir mayusculas). */
export function buscar(elementos: readonly Elemento[], consulta: string): Elemento[] {
  const q = consulta.trim().toLowerCase();
  if (!q) {
    return [...elementos];
  }
  return elementos.filter(
    (e) =>
      (e.titulo ?? '').toLowerCase().includes(q) ||
      e.url.toLowerCase().includes(q) ||
      e.etiquetas.some((etiqueta) => etiqueta.toLowerCase().includes(q)),
  );
}

/** Etiquetas distintas presentes, ordenadas: para el selector de filtro. */
export function etiquetasDisponibles(elementos: readonly Elemento[]): string[] {
  const unicas = new Set<string>();
  for (const e of elementos) {
    for (const etiqueta of e.etiquetas) {
      unicas.add(etiqueta);
    }
  }
  return [...unicas].sort();
}

/** Sin etiqueta (null o cadena vacia) no filtra nada. */
export function filtrarPorEtiqueta(
  elementos: readonly Elemento[],
  etiqueta: string | null,
): Elemento[] {
  if (!etiqueta) {
    return [...elementos];
  }
  return elementos.filter((e) => e.etiquetas.includes(etiqueta));
}
