/**
 * Resuelve los metadatos de una URL SIN pasar por el servidor: el telefono
 * descarga la pagina y la lee el mismo. Es lo que usa el modo local (sin
 * cuenta); con sesion iniciada sigue resolviendolos el backend, que es quien
 * los guarda para los dos clientes.
 *
 * Lo que aqui NO hace falta, y en el backend si: la comprobacion de que la URL
 * no apunta a una IP privada. Alli es imprescindible porque el servidor
 * descarga una URL que le manda otro (SSRF: podria alcanzar la red interna del
 * servidor); aqui la descarga el propio telefono, con la URL que ha escrito su
 * dueno, y no llega a ningun sitio al que no llegase ya Safari.
 */
import { MetadatosExtraidos, extraerMetadatos } from './extraccion';
import { esUrlYoutube, resolverOEmbedYoutube } from './youtube';

const TIMEOUT_MS = 8_000;

// Sin User-Agent propio: algunos sitios responden HTML distinto (o un muro) a
// lo que parece un robot, y aqui interesa justo lo que veria el navegador.
async function descargar(url: string, timeoutMs: number): Promise<string> {
  const controlador = new AbortController();
  const temporizador = setTimeout(() => controlador.abort(), timeoutMs);
  let respuesta: Response;
  try {
    respuesta = await fetch(url, { signal: controlador.signal });
  } catch {
    // Los mensajes de fetch (y del abort por tiempo) llegan en ingles y sin
    // contexto: esta pantalla los lee en voz alta, asi que se sustituyen.
    throw new Error('no se pudo abrir la página: sin conexión, tarda demasiado o no existe');
  } finally {
    clearTimeout(temporizador);
  }
  if (!respuesta.ok) {
    throw new Error(`la página respondió con un error ${respuesta.status}`);
  }
  return respuesta.text();
}

/**
 * Mismo orden que resolverMetadatos() en el backend: YouTube por oEmbed (mas
 * fiable que raspar og:*) y, si falla, la pagina entera.
 */
export async function resolverMetadatosEnDispositivo(
  url: string,
  timeoutMs: number = TIMEOUT_MS,
  descargarPagina: (url: string, timeoutMs: number) => Promise<string> = descargar,
): Promise<MetadatosExtraidos> {
  if (esUrlYoutube(url)) {
    const oembed = await resolverOEmbedYoutube(url, timeoutMs);
    if (oembed) {
      return oembed;
    }
  }
  return extraerMetadatos(await descargarPagina(url, timeoutMs));
}
