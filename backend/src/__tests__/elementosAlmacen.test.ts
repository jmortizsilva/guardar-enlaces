import { beforeEach, describe, expect, it } from 'vitest';
import { inicializarBd, obtenerBd } from '../db';
import { pull, push } from '../elementos/almacen';

beforeEach(() => {
  const bd = inicializarBd(':memory:');
  bd.prepare(
    `INSERT INTO usuarios (id, proveedor, id_proveedor, email, creado_en) VALUES (1, 'google', 'sub1', 'a@b.com', 0), (2, 'google', 'sub2', 'c@d.com', 0)`,
  ).run();
});

describe('push', () => {
  it('crea un elemento nuevo', () => {
    const [def] = push(1, [{ id: 'e1', url: 'https://a.com', titulo: 'A', actualizadoEn: 100 }]);
    expect(def.url).toBe('https://a.com');
    expect(def.titulo).toBe('A');
    expect(def.creadoEn).toBe(100);
    expect(def.borrado).toBe(false);
  });

  it('una edicion mas reciente gana', () => {
    push(1, [{ id: 'e1', url: 'https://a.com', titulo: 'A', actualizadoEn: 100 }]);
    const [def] = push(1, [{ id: 'e1', titulo: 'A editado', actualizadoEn: 200 }]);
    expect(def.titulo).toBe('A editado');
    expect(def.actualizadoEn).toBe(200);
    // creadoEn no cambia en una edicion
    expect(def.creadoEn).toBe(100);
  });

  it('una edicion con timestamp mas antiguo se ignora y devuelve la version del servidor', () => {
    push(1, [{ id: 'e1', url: 'https://a.com', titulo: 'A', actualizadoEn: 200 }]);
    const [def] = push(1, [{ id: 'e1', titulo: 'A viejo', actualizadoEn: 100 }]);
    expect(def.titulo).toBe('A');
    expect(def.actualizadoEn).toBe(200);
  });

  it('marcar borrado deja un tombstone, no borra la fila', () => {
    push(1, [{ id: 'e1', url: 'https://a.com', actualizadoEn: 100 }]);
    const [def] = push(1, [{ id: 'e1', actualizadoEn: 200, borrado: true }]);
    expect(def.borrado).toBe(true);

    const fila = obtenerBd().prepare('SELECT * FROM elementos WHERE id = ?').get('e1');
    expect(fila).toBeDefined();
  });

  it('no permite pisar un elemento de otro usuario con el mismo id', () => {
    push(1, [{ id: 'compartido', url: 'https://a.com', titulo: 'De usuario 1', actualizadoEn: 100 }]);
    const definitivos = push(2, [
      { id: 'compartido', titulo: 'Intento de usuario 2', actualizadoEn: 999 },
    ]);
    expect(definitivos).toHaveLength(0);

    const fila = obtenerBd().prepare('SELECT * FROM elementos WHERE id = ?').get('compartido') as {
      titulo: string;
      usuario_id: number;
    };
    expect(fila.titulo).toBe('De usuario 1');
    expect(fila.usuario_id).toBe(1);
  });

  it('un timestamp muy en el futuro se recorta al reloj del servidor', () => {
    const ahora = () => 1_000_000;
    const [def] = push(
      1,
      [{ id: 'e1', url: 'https://a.com', actualizadoEn: 1_000_000 + 999_999_999 }],
      ahora,
    );
    expect(def.actualizadoEn).toBeLessThanOrEqual(1_000_000 + 5 * 60 * 1000);
  });

  it('un alta sin url se ignora', () => {
    const definitivos = push(1, [{ id: 'e1', actualizadoEn: 100 }]);
    expect(definitivos).toHaveLength(0);
  });
});

describe('pull', () => {
  it('solo devuelve elementos del usuario pedido, posteriores a "desde"', () => {
    push(1, [{ id: 'e1', url: 'https://a.com', actualizadoEn: 100 }]);
    push(2, [{ id: 'e2', url: 'https://b.com', actualizadoEn: 100 }]);
    push(1, [{ id: 'e3', url: 'https://c.com', actualizadoEn: 200 }]);

    const resultado = pull(1, 100, 300);
    expect(resultado.elementos.map((e) => e.id)).toEqual(['e3']);
  });

  it('desde=0 trae todo, incluidos los tombstones', () => {
    push(1, [{ id: 'e1', url: 'https://a.com', actualizadoEn: 100 }]);
    push(1, [{ id: 'e1', actualizadoEn: 200, borrado: true }]);

    const resultado = pull(1, 0, 300);
    expect(resultado.elementos).toHaveLength(1);
    expect(resultado.elementos[0].borrado).toBe(true);
  });

  it('respeta el limite y marca masDisponible', () => {
    push(
      1,
      [1, 2, 3].map((n) => ({ id: `e${n}`, url: `https://${n}.com`, actualizadoEn: n * 10 })),
    );
    const resultado = pull(1, 0, 2);
    expect(resultado.elementos).toHaveLength(2);
    expect(resultado.masDisponible).toBe(true);
    expect(resultado.elementos.map((e) => e.id)).toEqual(['e1', 'e2']);
  });
});
