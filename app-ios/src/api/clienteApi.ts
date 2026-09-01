/**
 * Cliente HTTP del backend. Ver el contrato completo en
 * ../../backend/docs/CONTRATO-API.md.
 *
 * Fontaneria pura: no decide nada, solo traduce llamadas a peticiones HTTP y
 * errores HTTP a excepciones con mensaje en castellano. Devuelve el JSON tal
 * cual llega (camelCase, ya es el formato del contrato); convertir esos
 * datos a `Elemento` es responsabilidad de quien llama (src/sincronizador).
 */

export class ErrorApi extends Error {
  readonly statusCode?: number;

  constructor(mensaje: string, statusCode?: number) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.statusCode = statusCode;
  }
}

export interface UsuarioApi {
  id: number;
  email: string;
  proveedor: string;
}

export interface RespuestaCanje {
  tokenAcceso: string;
  expiraEn: number;
  tokenRefresco: string;
  usuario?: UsuarioApi;
}

export interface RespuestaMetadatos {
  titulo: string | null;
  descripcion: string | null;
  imagenUrl: string | null;
  tipo: string;
}

export interface RespuestaSincronizar {
  elementos: Record<string, unknown>[];
  servidorEn: number;
  masDisponible: boolean;
}

// Subido temporalmente de 10s a 30s para diagnosticar peticiones lentas
// contra api.jmortiz.es (posible negociacion HTTP/3 lenta del fetch nativo
// de Expo SDK 56 contra el proxy Caddy). Volver a 10s si se descarta.
const TIMEOUT_MS_POR_DEFECTO = 30_000;

export class ClienteApi {
  private readonly urlBase: string;
  private readonly timeoutMs: number;

  constructor(urlBase: string, timeoutMs: number = TIMEOUT_MS_POR_DEFECTO) {
    this.urlBase = urlBase.replace(/\/+$/, '');
    this.timeoutMs = timeoutMs;
  }

  // --- autenticacion ---

  /** SOLO sirve si el servidor tiene PERMITIR_LOGIN_DEV=true. */
  devLogin(email: string): Promise<RespuestaCanje> {
    return this.post('/auth/dev-login', { email });
  }

  renovar(tokenRefresco: string): Promise<RespuestaCanje> {
    return this.post('/auth/renovar', { tokenRefresco });
  }

  async logout(tokenRefresco: string): Promise<void> {
    await this.post('/auth/logout', { tokenRefresco });
  }

  // --- metadatos ---

  metadatos(url: string, tokenAcceso: string): Promise<RespuestaMetadatos> {
    return this.post('/metadatos', { url }, tokenAcceso);
  }

  // --- sincronizacion ---

  pull(desde: number, tokenAcceso: string, limite = 300): Promise<RespuestaSincronizar> {
    const parametros = new URLSearchParams({ desde: String(desde), limite: String(limite) });
    return this.get(`/sincronizar?${parametros.toString()}`, tokenAcceso);
  }

  push(
    elementos: readonly Record<string, unknown>[],
    tokenAcceso: string,
  ): Promise<RespuestaSincronizar> {
    return this.post('/sincronizar', { elementos }, tokenAcceso);
  }

  // --- internals ---

  private cabeceras(tokenAcceso: string | undefined): Record<string, string> {
    return tokenAcceso ? { Authorization: `Bearer ${tokenAcceso}` } : {};
  }

  private async post<T>(ruta: string, cuerpo: unknown, tokenAcceso?: string): Promise<T> {
    const respuesta = await this.enviar(ruta, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...this.cabeceras(tokenAcceso) },
      body: JSON.stringify(cuerpo),
    });
    return procesarRespuesta<T>(respuesta);
  }

  private async get<T>(ruta: string, tokenAcceso?: string): Promise<T> {
    const respuesta = await this.enviar(ruta, {
      method: 'GET',
      headers: this.cabeceras(tokenAcceso),
    });
    return procesarRespuesta<T>(respuesta);
  }

  private async enviar(ruta: string, opciones: RequestInit): Promise<Response> {
    const controlador = new AbortController();
    const temporizador = setTimeout(() => controlador.abort(), this.timeoutMs);
    try {
      return await fetch(`${this.urlBase}${ruta}`, { ...opciones, signal: controlador.signal });
    } catch (error) {
      throw new ErrorApi(`no se pudo conectar con el servidor: ${String(error)}`);
    } finally {
      clearTimeout(temporizador);
    }
  }
}

async function procesarRespuesta<T>(respuesta: Response): Promise<T> {
  if (!respuesta.ok) {
    throw new ErrorApi(await mensajeDeError(respuesta), respuesta.status);
  }
  const texto = await respuesta.text();
  return (texto ? JSON.parse(texto) : {}) as T;
}

async function mensajeDeError(respuesta: Response): Promise<string> {
  try {
    const cuerpo = await respuesta.json();
    return cuerpo?.error ?? `error ${respuesta.status}`;
  } catch {
    return `error ${respuesta.status}`;
  }
}
