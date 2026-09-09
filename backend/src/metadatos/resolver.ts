import { lookup } from 'node:dns/promises';
import { extraerMetadatos, MetadatosExtraidos } from './extraccion';
import { esIpPrivada } from './ssrf';
import { esUrlYoutube, resolverOEmbedYoutube } from './youtube';

const TIMEOUT_MS = 5_000;
const LIMITE_BYTES = 2_000_000;
const MAX_REDIRECCIONES = 5;
const USER_AGENT = 'GuardarEnlacesBot/1.0 (+resolucion de metadatos para guardar-enlaces)';

class UrlNoPermitidaError extends Error {}

// Nota de seguridad: esto resuelve el DNS UNA VEZ para comprobar que no es una IP privada, pero
// `fetch()` vuelve a resolver el DNS el solo para conectar. Entre medias hay una ventana teorica
// de "DNS rebinding" (el dominio podria cambiar de IP justo despues de la comprobacion). Cerrarlo
// del todo exigiria un Agent/dispatcher que fije la conexion a la IP ya validada; para el tamano
// de este proyecto no compensa esa complejidad ahora — mitigacion basica, documentada, no
// exhaustiva. OJO: desde que el alta es abierta (cualquiera con cuenta de Google o Apple entra),
// esta ruta la puede llamar cualquiera, no un grupo cerrado como cuando se escribio esto; sigue
// exigiendo sesion y limite de peticiones, pero el atacante ya no tiene que estar invitado.
async function comprobarNoEsIpPrivada(hostname: string): Promise<void> {
  const direcciones = await lookup(hostname, { all: true });
  if (direcciones.some((d) => esIpPrivada(d.address))) {
    throw new UrlNoPermitidaError('la URL resuelve a una direccion de red privada');
  }
}

async function descargarConLimite(urlInicial: string): Promise<string> {
  let urlActual = urlInicial;

  for (let salto = 0; salto < MAX_REDIRECCIONES; salto++) {
    const destino = new URL(urlActual);
    if (destino.protocol !== 'http:' && destino.protocol !== 'https:') {
      throw new UrlNoPermitidaError('protocolo no soportado');
    }
    await comprobarNoEsIpPrivada(destino.hostname);

    const controlador = new AbortController();
    const temporizador = setTimeout(() => controlador.abort(), TIMEOUT_MS);
    let respuesta: Response;
    try {
      respuesta = await fetch(urlActual, {
        signal: controlador.signal,
        redirect: 'manual',
        headers: { 'User-Agent': USER_AGENT },
      });
    } finally {
      clearTimeout(temporizador);
    }

    if (respuesta.status >= 300 && respuesta.status < 400) {
      const ubicacion = respuesta.headers.get('location');
      if (!ubicacion) {
        throw new Error('redireccion sin cabecera Location');
      }
      urlActual = new URL(ubicacion, urlActual).toString();
      continue;
    }

    if (!respuesta.ok || !respuesta.body) {
      throw new Error(`respuesta ${respuesta.status} al descargar la URL`);
    }
    return leerConLimite(respuesta.body, LIMITE_BYTES);
  }

  throw new Error('demasiadas redirecciones');
}

async function leerConLimite(cuerpo: ReadableStream<Uint8Array>, limiteBytes: number): Promise<string> {
  const lector = cuerpo.getReader();
  const trozos: Uint8Array[] = [];
  let recibidos = 0;
  for (;;) {
    const { done, value } = await lector.read();
    if (done) {
      break;
    }
    recibidos += value.byteLength;
    if (recibidos > limiteBytes) {
      await lector.cancel();
      break;
    }
    trozos.push(value);
  }
  return Buffer.concat(trozos).toString('utf8');
}

// Punto de entrada: resuelve titulo/descripcion/imagen/tipo a partir de una URL. Caso especial
// YouTube via oEmbed (mas fiable que el scraping generico); si falla, cae al scraping normal.
export async function resolverMetadatos(urlTexto: string): Promise<MetadatosExtraidos> {
  if (esUrlYoutube(urlTexto)) {
    const destino = new URL(urlTexto);
    await comprobarNoEsIpPrivada(destino.hostname);
    const oembed = await resolverOEmbedYoutube(urlTexto, TIMEOUT_MS);
    if (oembed) {
      return oembed;
    }
  }

  const html = await descargarConLimite(urlTexto);
  return extraerMetadatos(html);
}

export { UrlNoPermitidaError };
