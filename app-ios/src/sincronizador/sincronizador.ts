/**
 * Orquesta un ciclo de sincronizacion completo: sube lo pendiente, luego
 * baja lo nuevo del servidor. La decision de como fusionar vive en
 * src/dominio; aqui solo se encadenan almacen + api + sesion.
 */
import { ClienteApi } from '../api/clienteApi';
import { Elemento, elementoAJson, elementoDesdeJson } from '../dominio/elemento';
import { aplicarPull, aplicarRespuestaPush, Cache } from '../dominio/sincronizacion';
import { Sesion } from '../sesion/sesion';

/**
 * Lo que Sincronizador necesita del almacen local. AlmacenLocal (SQLite) lo
 * cumple por estructura; en tests se usa AlmacenEnMemoria porque
 * expo-sqlite no corre en Jest/Node.
 */
export interface AlmacenSincronizable {
  cursor(): number;
  fijarCursor(valor: number): void;
  cargarTodos(): Cache;
  cargarPendientes(): Cache;
  guardar(cache: Cache): void;
  marcarPendiente(elemento: Elemento): void;
  limpiarPendientes(ids: readonly string[]): void;
}

export class Sincronizador {
  constructor(
    private readonly almacen: AlmacenSincronizable,
    private readonly cliente: ClienteApi,
    private readonly sesion: Sesion,
  ) {}

  async sincronizar(): Promise<void> {
    await this.subirPendientes();
    await this.bajarCambios();
  }

  private async subirPendientes(): Promise<void> {
    const pendientes = this.almacen.cargarPendientes();
    const lote = Object.values(pendientes);
    if (lote.length === 0) {
      return;
    }
    const cuerpo = lote.map(elementoAJson);
    const respuesta = await this.sesion.conReintento((token) => this.cliente.push(cuerpo, token));
    const definitivos = respuesta.elementos.map(elementoDesdeJson);

    const cache = aplicarRespuestaPush(this.almacen.cargarTodos(), definitivos);
    this.almacen.guardar(cache);
    this.almacen.limpiarPendientes(definitivos.map((e) => e.id));
  }

  private async bajarCambios(): Promise<void> {
    // Repite el pull mientras el servidor diga que hay mas paginas (biblioteca
    // grande o primera sincronizacion); ver "masDisponible" en CONTRATO-API.md.
    for (;;) {
      const desde = this.almacen.cursor();
      const respuesta = await this.sesion.conReintento((token) => this.cliente.pull(desde, token));
      const recibidos = respuesta.elementos.map(elementoDesdeJson);

      const cache = aplicarPull(
        this.almacen.cargarTodos(),
        recibidos,
        this.almacen.cargarPendientes(),
      );
      this.almacen.guardar(cache);

      if (respuesta.masDisponible && recibidos.length > 0) {
        this.almacen.fijarCursor(Math.max(...recibidos.map((e) => e.actualizadoEn)));
      } else {
        this.almacen.fijarCursor(respuesta.servidorEn);
        break;
      }
    }
  }
}
