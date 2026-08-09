/**
 * Estado de sesion: token de acceso en memoria, token de refresco
 * persistido via credenciales.ts (Keychain de iOS).
 */
import { ClienteApi, ErrorApi, RespuestaCanje, UsuarioApi } from '../api/clienteApi';
import * as credenciales from './credenciales';

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

  /** SOLO sirve si el servidor tiene PERMITIR_LOGIN_DEV=true. */
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
