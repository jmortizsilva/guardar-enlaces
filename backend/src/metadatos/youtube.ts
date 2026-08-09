import { MetadatosExtraidos } from './extraccion';

const DOMINIOS_YOUTUBE = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be']);

export function esUrlYoutube(url: string): boolean {
  try {
    return DOMINIOS_YOUTUBE.has(new URL(url).hostname);
  } catch {
    return false;
  }
}

interface RespuestaOEmbed {
  title?: string;
  thumbnail_url?: string;
}

// oEmbed publico de YouTube: da titulo y miniatura mas fiables que el scraping generico de
// og:*, sin necesitar credenciales de la API de YouTube.
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
    return null; // si falla, resolverMetadatos cae al scraping generico
  } finally {
    clearTimeout(temporizador);
  }
}
