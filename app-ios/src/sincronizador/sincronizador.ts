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

  /** Devuelve cuantos cambios locales rechazo el servidor (normalmente 0). */
  async sincronizar(): Promise<number> {
    const rechazados = await this.subirPendientes();
    await this.bajarCambios();
    return rechazados;
  }

  private async subirPendientes(): Promise<number> {
    const pendientes = this.almacen.cargarPendientes();
    const lote = Object.values(pendientes);
    if (lote.length === 0) {
      return 0;
    }
    const cuerpo = lote.map(elementoAJson);
    const respuesta = await this.sesion.conReintento((token) => this.cliente.push(cuerpo, token));
    const definitivos = respuesta.elementos.map(elementoDesdeJson);

    const cache = aplicarRespuestaPush(this.almacen.cargarTodos(), definitivos);
    this.almacen.guardar(cache);

    // Lo rechazado sale del outbox igual que lo aceptado. El servidor no lo va a
    // admitir por mucho que se insista, y dejarlo dentro reenvia el lote entero
    // en cada sincronizacion, para siempre y sin que se note (asi se quedaron
    // atascados los enlaces al importarlos a una cuenta nueva).
    const rechazados = respuesta.rechazados ?? [];
    this.almacen.limpiarPendientes([
      ...definitivos.map((e) => e.id),
      ...rechazados.map((r) => r.id),
    ]);
    return rechazados.length;
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
