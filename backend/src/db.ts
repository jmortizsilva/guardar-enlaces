import Database from 'better-sqlite3';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { config } from './config';
import { SCHEMA_SQL } from './esquema';

let bd: Database.Database | undefined;

// Abre (o crea) la base de datos y aplica el esquema. Con ':memory:' se usa una BD en memoria,
// util para los tests. Se llama una vez al arrancar.
export function inicializarBd(rutaBd: string = config.rutaBd): Database.Database {
  if (rutaBd !== ':memory:') {
    mkdirSync(dirname(rutaBd), { recursive: true });
  }
  const db = new Database(rutaBd);
  db.pragma('journal_mode = WAL');
  db.exec(SCHEMA_SQL);
  bd = db;
  return db;
}

export function obtenerBd(): Database.Database {
  if (!bd) {
    throw new Error('Base de datos no inicializada: llama a inicializarBd() primero');
  }
  return bd;
}
