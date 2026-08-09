/**
 * Formateo de texto para mostrar un elemento (logica pura, sin componentes:
 * FilaLista compone accessibilityLabel = "titulo. subtitulo", asi que
 * subtitulo debe ser un texto igual de valido leido que visto).
 */
import { Elemento } from './elemento';

export function tituloFila(elemento: Elemento): string {
  return elemento.titulo || elemento.url;
}

export function subtituloFila(elemento: Elemento): string {
  const partes: string[] = [];

  const dominio = dominioDeUrl(elemento.url);
  if (dominio) {
    partes.push(dominio);
  }

  if (elemento.etiquetas.length > 0) {
    partes.push(elemento.etiquetas.join(', '));
  }

  partes.push(fechaLegible(elemento.actualizadoEn));

  return partes.join(' — ');
}

function dominioDeUrl(url: string): string | null {
  try {
    return new URL(url).hostname || null;
  } catch {
    return null;
  }
}

/**
 * Fecha con mes en letra: "09/08/2026" se lee dígito a dígito con VoiceOver
 * (formatos.ts, el mismo motivo que evita "12:34" para una duración).
 */
function fechaLegible(timestampMs: number): string {
  if (!timestampMs) {
    return 'sin fecha';
  }
  return new Intl.DateTimeFormat('es-ES', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(timestampMs));
}
