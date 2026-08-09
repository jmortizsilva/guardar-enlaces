/**
 * Doble de AlmacenLocal para tests: mismo contrato (AlmacenSincronizable),
 * sin SQLite real. expo-sqlite es un modulo nativo (JSI) y no tiene
 * equivalente al ":memory:" de sqlite3 ejecutable en Jest/Node, a diferencia
 * de la version Python (ver Sincronizador.test.ts).
 */
import { Elemento } from '../dominio/elemento';
import { Cache } from '../dominio/sincronizacion';
import { AlmacenSincronizable } from '../sincronizador/sincronizador';

export class AlmacenEnMemoria implements AlmacenSincronizable {
  private elementos: Cache = {};
  private readonly pendientes = new Set<string>();
  private cursorActual = 0;

  cursor(): number {
    return this.cursorActual;
  }

  fijarCursor(valor: number): void {
    this.cursorActual = valor;
  }

  cargarTodos(): Cache {
    return { ...this.elementos };
  }

  cargarPendientes(): Cache {
    const resultado: Cache = {};
    for (const id of this.pendientes) {
      const elemento = this.elementos[id];
      if (elemento) {
        resultado[id] = elemento;
      }
    }
    return resultado;
  }

  guardar(cache: Cache): void {
    this.elementos = { ...this.elementos, ...cache };
  }

  marcarPendiente(elemento: Elemento): void {
    this.elementos[elemento.id] = elemento;
    this.pendientes.add(elemento.id);
  }

  limpiarPendientes(ids: readonly string[]): void {
    for (const id of ids) {
      this.pendientes.delete(id);
    }
  }
}
