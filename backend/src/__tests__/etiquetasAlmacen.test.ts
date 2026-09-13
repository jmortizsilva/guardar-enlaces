import { beforeEach, describe, expect, it } from 'vitest';
import { inicializarBd, obtenerBd } from '../db';
import { pull, push } from '../etiquetas/almacen';

beforeEach(() => {
  const bd = inicializarBd(':memory:');
  bd.prepare(
    `INSERT INTO usuarios (id, proveedor, id_proveedor, email, creado_en) VALUES (1, 'google', 'sub1', 'a@b.com', 0), (2, 'google', 'sub2', 'c@d.com', 0)`,
  ).run();
});

describe('push', () => {
  it('crea una etiqueta nueva', () => {
    const [def] = push(1, [{ id: 't1', nombre: 'ocio', actualizadoEn: 100 }]).etiquetasDefinidas;
    expect(def.nombre).toBe('ocio');
    expect(def.creadoEn).toBe(100);
    expect(def.borrado).toBe(false);
  });

  it('una edicion mas reciente gana', () => {
    push(1, [{ id: 't1', nombre: 'ocio', actualizadoEn: 100 }]);
    const [def] = push(1, [{ id: 't1', nombre: 'hobby', actualizadoEn: 200 }]).etiquetasDefinidas;
    expect(def.nombre).toBe('hobby');
    expect(def.actualizadoEn).toBe(200);
    expect(def.creadoEn).toBe(100);
  });

  it('una edicion con timestamp mas antiguo se ignora y devuelve la version del servidor', () => {
    push(1, [{ id: 't1', nombre: 'ocio', actualizadoEn: 200 }]);
    const [def] = push(1, [{ id: 't1', nombre: 'viejo', actualizadoEn: 100 }]).etiquetasDefinidas;
    expect(def.nombre).toBe('ocio');
    expect(def.actualizadoEn).toBe(200);
  });

  it('marcar borrado deja un tombstone, no borra la fila', () => {
    push(1, [{ id: 't1', nombre: 'ocio', actualizadoEn: 100 }]);
    const [def] = push(1, [{ id: 't1', actualizadoEn: 200, borrado: true }]).etiquetasDefinidas;
    expect(def.borrado).toBe(true);

    const fila = obtenerBd().prepare('SELECT * FROM etiquetas_definidas WHERE id = ?').get('t1');
    expect(fila).toBeDefined();
  });

  it('no permite pisar una etiqueta de otro usuario con el mismo id', () => {
    push(1, [{ id: 'compartido', nombre: 'de uno', actualizadoEn: 100 }]);
    const resultado = push(2, [{ id: 'compartido', nombre: 'intento', actualizadoEn: 999 }]);
    expect(resultado.etiquetasDefinidas).toHaveLength(0);
    expect(resultado.rechazadas).toEqual([{ id: 'compartido', motivo: 'no_aplicable' }]);
  });

  it('un alta sin nombre se rechaza, y se dice', () => {
    const resultado = push(1, [{ id: 't1', actualizadoEn: 100 }]);
    expect(resultado.etiquetasDefinidas).toHaveLength(0);
    expect(resultado.rechazadas).toEqual([{ id: 't1', motivo: 'sin_nombre' }]);
  });
});

describe('pull', () => {
  it('solo devuelve etiquetas del usuario pedido, posteriores a "desde"', () => {
    push(1, [{ id: 't1', nombre: 'a', actualizadoEn: 100 }]);
    push(2, [{ id: 't2', nombre: 'b', actualizadoEn: 100 }]);
    push(1, [{ id: 't3', nombre: 'c', actualizadoEn: 200 }]);

    const resultado = pull(1, 100);
    expect(resultado.etiquetasDefinidas.map((e) => e.id)).toEqual(['t3']);
  });

  it('desde=0 trae todo, incluidos los tombstones', () => {
    push(1, [{ id: 't1', nombre: 'a', actualizadoEn: 100 }]);
    push(1, [{ id: 't1', actualizadoEn: 200, borrado: true }]);

    const resultado = pull(1, 0);
    expect(resultado.etiquetasDefinidas).toHaveLength(1);
    expect(resultado.etiquetasDefinidas[0].borrado).toBe(true);
  });
});
