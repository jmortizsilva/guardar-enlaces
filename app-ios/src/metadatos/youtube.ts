/**
 * COPIA de backend/src/metadatos/youtube.ts (ver la nota de extraccion.ts).
 */
import { MetadatosExtraidos } from './extraccion';

const DOMINIOS_YOUTUBE = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be']);

export function esUrlYoutube(url: string): boolean {
  try {
    // El URL de React Native es de mentirijillas, pero `hostname` si funciona
    // con http y https, que es lo unico que llega aqui (ver loginProveedor.ts).
    return DOMINIOS_YOUTUBE.has(new URL(url).hostname);
  } catch {
    return false;
  }
}

interface RespuestaOEmbed {
  title?: string;
  thumbnail_url?: string;
}

// oEmbed publico de YouTube: da titulo y miniatura mas fiables que el raspado generico de og:*,
// y no necesita credenciales de la API de YouTube.
export async function resolverOEmbedYoutube(
  url: string,
  timeoutMs: number,
): Promise<MetadatosExtraidos | null> {
  const controlador = new AbortController();
  const temporizador = setTimeout(() => controlador.abort(), timeoutMs);
  try {
    const respuesta = await fetch(
      `https://www.youtube.com/oembed?url=${encodeURIComponent(url)}&format=json`,
      { signal: controlador.signal },
    );
    if (!respuesta.ok) {
      return null;
    }
    const datos = (await respuesta.json()) as RespuestaOEmbed;
    return {
      titulo: datos.title ?? null,
      descripcion: null,
      imagenUrl: datos.thumbnail_url ?? null,
      tipo: 'video',
    };
  } catch {
    return null; // si falla, se cae al raspado generico
  } finally {
    clearTimeout(temporizador);
  }
}
