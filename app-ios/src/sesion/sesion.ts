/**
 * Estado de sesion: token de acceso en memoria, token de refresco
 * persistido via credenciales.ts (Keychain de iOS).
 */
import { ClienteApi, ErrorApi, RespuestaCanje, UsuarioApi } from '../api/clienteApi';
import * as credenciales from './credenciales';
import {
  ESQUEMA,
  Proveedor,
  ResultadoLogin,
  generarEstado,
  pedirCodigoCanje,
} from './loginProveedor';

/** Inyectables solo para las pruebas: en la app siempre son los de loginProveedor. */
interface DependenciasLogin {
  generarEstado: () => Promise<string>;
  pedirCodigoCanje: (urlAutorizacion: string) => Promise<ResultadoLogin>;
}

export class Sesion {
  private readonly cliente: ClienteApi;
  tokenAcceso: string | null = null;
  usuario: UsuarioApi | null = null;

  constructor(cliente: ClienteApi) {
    this.cliente = cliente;
  }

  get autenticado(): boolean {
    return this.tokenAcceso !== null;
  }

  /**
   * Login real: abre el consentimiento del proveedor en el navegador del
   * sistema y, si vuelve con codigo de canje, lo cambia por tokens.
   *
   * Devuelve el resultado en vez de lanzar cuando el usuario cancela o el
   * proveedor rechaza: eso no es una averia, y la pantalla lo cuenta distinto.
   * Si falla /auth/canjear si se propaga el ErrorApi.
   */
  async iniciarConProveedor(
    proveedor: Proveedor,
    dependencias: DependenciasLogin = { generarEstado, pedirCodigoCanje },
  ): Promise<ResultadoLogin> {
    const estado = await dependencias.generarEstado();
    const url = this.cliente.urlIniciarLogin(proveedor, estado, ESQUEMA);
    const resultado = await dependencias.pedirCodigoCanje(url);
    if (resultado.estado === 'exito') {
      await this.aplicarTokens(await this.cliente.canjear(resultado.codigoCanje));
    }
    return resultado;
  }

  /** SOLO sirve si el servidor tiene PERMITIR_LOGIN_DEV=true (ver ClienteApi.devLogin). */
  async iniciarConDevLogin(email: string): Promise<void> {
    await this.aplicarTokens(await this.cliente.devLogin(email));
  }

  /**
   * Intenta recuperar la sesion con el token de refresco guardado de una
   * vez anterior. Devuelve false si no habia, o si ya no sirve.
   */
  async restaurar(): Promise<boolean> {
    const tokenRefresco = await credenciales.obtenerTokenRefresco();
    if (!tokenRefresco) {
      return false;
    }
    try {
      await this.aplicarTokens(await this.cliente.renovar(tokenRefresco));
    } catch (error) {
      if (error instanceof ErrorApi) {
        await credenciales.borrarTokenRefresco();
        return false;
      }
      throw error;
    }
    return true;
  }

  async cerrar(): Promise<void> {
    const tokenRefresco = await credenciales.obtenerTokenRefresco();
    if (tokenRefresco) {
      try {
        await this.cliente.logout(tokenRefresco);
      } catch (error) {
        if (!(error instanceof ErrorApi)) {
          throw error;
        }
        // cerrar sesion localmente aunque el servidor no responda
      }
    }
    await credenciales.borrarTokenRefresco();
    this.tokenAcceso = null;
    this.usuario = null;
  }

  /**
   * Ejecuta funcion(tokenAcceso); si el servidor dice 401 (token de acceso
   * caducado), renueva una vez con el token de refresco y reintenta. Si la
   * renovacion tambien falla, se propaga el error (la interfaz debe volver
   * a pedir inicio de sesion).
   */
  async conReintento<T>(funcion: (tokenAcceso: string) => Promise<T>): Promise<T> {
    try {
      return await funcion(this.tokenAcceso as string);
    } catch (error) {
      if (!(error instanceof ErrorApi) || error.statusCode !== 401 || !(await this.restaurar())) {
        throw error;
      }
      return funcion(this.tokenAcceso as string);
    }
  }

  private async aplicarTokens(datos: RespuestaCanje): Promise<void> {
    this.tokenAcceso = datos.tokenAcceso;
    if (datos.usuario) {
      this.usuario = datos.usuario;
    }
    if (datos.tokenRefresco) {
      await credenciales.guardarTokenRefresco(datos.tokenRefresco);
    }
  }
}
