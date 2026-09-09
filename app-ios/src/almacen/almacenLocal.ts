/**
 * Cache local (SQLite) + "outbox" de cambios pendientes de subir.
 *
 * Fontaneria: aqui se persiste, la decision de como fusionar vive en
 * src/dominio (logica pura, ya probada). El outbox es sencillo a proposito:
 * solo el CONJUNTO de ids con un cambio local sin confirmar. El elemento en
 * si ya esta en la tabla `elementos` (se actualiza ahi mismo al hacer el
 * cambio local, para que la interfaz lo vea al instante sin esperar a la
 * red).
 *
 * API sincrona (expo-sqlite `*Sync`): no hay hilo de fondo separado en RN
 * como en Python, y evita convertir cada pantalla en async solo por leer la
 * cache local.
 */
import * as SQLite from 'expo-sqlite';

import { Elemento, TipoElemento } from '../dominio/elemento';
import { Cache } from '../dominio/sincronizacion';

interface FilaElemento {
  id: string;
  url: string;
  titulo: string | null;
  descripcion: string | null;
  imagen_url: string | null;
  tipo: string;
  etiquetas: string;
  creado_en: number;
  actualizado_en: number;
  borrado: number;
}

export class AlmacenLocal {
  private readonly db: SQLite.SQLiteDatabase;

  constructor(nombreBaseDatos = 'guardar-enlaces.db') {
    this.db = SQLite.openDatabaseSync(nombreBaseDatos);
    this.crearEsquema();
  }

  private crearEsquema(): void {
    this.db.execSync(`
      CREATE TABLE IF NOT EXISTS elementos (
        id TEXT PRIMARY KEY,
        url TEXT NOT NULL,
        titulo TEXT,
        descripcion TEXT,
        imagen_url TEXT,
        tipo TEXT NOT NULL DEFAULT 'enlace',
        etiquetas TEXT NOT NULL DEFAULT '[]',
        creado_en INTEGER NOT NULL,
        actualizado_en INTEGER NOT NULL,
        borrado INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS outbox (
        id TEXT PRIMARY KEY
      );
      CREATE TABLE IF NOT EXISTS estado_sincronizacion (
        clave TEXT PRIMARY KEY,
        valor INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS estado_texto (
        clave TEXT PRIMARY KEY,
        valor TEXT NOT NULL
      );
    `);
  }

  cerrar(): void {
    this.db.closeSync();
  }

  /**
   * Borra la cache entera: elementos, outbox, cursor y dueno. Se llama al
   * cerrar sesion y al entrar alguien distinto (ver ProveedorApp).
   */
  vaciar(): void {
    this.db.execSync(`
      DELETE FROM elementos;
      DELETE FROM outbox;
      DELETE FROM estado_sincronizacion;
      DELETE FROM estado_texto;
    `);
  }

  // --- dueno de la cache (que cuenta, y de que servidor, dejo estos datos) ---

  duenoActual(): string | null {
    const fila = this.db.getFirstSync<{ valor: string }>(
      "SELECT valor FROM estado_texto WHERE clave = 'dueno'",
    );
    return fila?.valor ?? null;
  }

  fijarDueno(valor: string): void {
    this.db.runSync(
      `INSERT INTO estado_texto (clave, valor) VALUES ('dueno', ?)
       ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor`,
      [valor],
    );
  }

  // --- cursor de sincronizacion (ultimo "servidorEn" recibido) ---

  cursor(): number {
    const fila = this.db.getFirstSync<{ valor: number }>(
      "SELECT valor FROM estado_sincronizacion WHERE clave = 'cursor'",
    );
    return fila?.valor ?? 0;
  }

  fijarCursor(valor: number): void {
    this.db.runSync(
      `INSERT INTO estado_sincronizacion (clave, valor) VALUES ('cursor', ?)
       ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor`,
      [valor],
    );
  }

  // --- elementos ---

  cargarTodos(): Cache {
    const filas = this.db.getAllSync<FilaElemento>('SELECT * FROM elementos');
    return Object.fromEntries(filas.map((fila) => [fila.id, elementoDeFila(fila)]));
  }

  cargarPendientes(): Cache {
    const filas = this.db.getAllSync<FilaElemento>(
      'SELECT e.* FROM elementos e JOIN outbox o ON o.id = e.id',
    );
    return Object.fromEntries(filas.map((fila) => [fila.id, elementoDeFila(fila)]));
  }

  /**
   * Persiste el resultado de aplicarPull/aplicarRespuestaPush (no toca el
   * outbox: eso lo decide quien orquesta la sincronizacion).
   */
  guardar(cache: Cache): void {
    for (const elemento of Object.values(cache)) {
      this.upsertElemento(elemento);
    }
  }

  /** Cambio local (alta/edicion/baja): se ve al instante y se encola para el proximo push. */
  marcarPendiente(elemento: Elemento): void {
    this.upsertElemento(elemento);
    this.db.runSync('INSERT OR IGNORE INTO outbox (id) VALUES (?)', [elemento.id]);
  }

  limpiarPendientes(ids: readonly string[]): void {
    for (const id of ids) {
      this.db.runSync('DELETE FROM outbox WHERE id = ?', [id]);
    }
  }

  private upsertElemento(elemento: Elemento): void {
    this.db.runSync(
      `INSERT INTO elementos
         (id, url, titulo, descripcion, imagen_url, tipo, etiquetas, creado_en, actualizado_en, borrado)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         url = excluded.url, titulo = excluded.titulo, descripcion = excluded.descripcion,
         imagen_url = excluded.imagen_url, tipo = excluded.tipo, etiquetas = excluded.etiquetas,
         creado_en = excluded.creado_en, actualizado_en = excluded.actualizado_en,
         borrado = excluded.borrado`,
      [
        elemento.id,
        elemento.url,
        elemento.titulo,
        elemento.descripcion,
        elemento.imagenUrl,
        elemento.tipo,
        JSON.stringify(elemento.etiquetas),
        elemento.creadoEn,
        elemento.actualizadoEn,
        elemento.borrado ? 1 : 0,
      ],
    );
  }
}

function elementoDeFila(fila: FilaElemento): Elemento {
  return {
    id: fila.id,
    url: fila.url,
    titulo: fila.titulo,
    descripcion: fila.descripcion,
    imagenUrl: fila.imagen_url,
    tipo: fila.tipo as TipoElemento,
    etiquetas: JSON.parse(fila.etiquetas),
    creadoEn: fila.creado_en,
    actualizadoEn: fila.actualizado_en,
    borrado: Boolean(fila.borrado),
  };
}
