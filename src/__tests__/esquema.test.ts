import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { SCHEMA_SQL } from '../esquema';

// Quita comentarios (-- ...) y colapsa espacios, para comparar solo las sentencias.
function normalizar(sql: string): string {
  return sql
    .split('\n')
    .filter((linea) => !linea.trim().startsWith('--'))
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();
}

describe('esquema', () => {
  it('schema.sql coincide con la constante SCHEMA_SQL (fuente de la verdad)', () => {
    const fichero = readFileSync(join(process.cwd(), 'schema.sql'), 'utf8');
    expect(normalizar(fichero)).toBe(normalizar(SCHEMA_SQL));
  });
});
