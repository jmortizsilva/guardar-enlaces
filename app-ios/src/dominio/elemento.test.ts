import {
  editar,
  elementoAJson,
  elementoDesdeJson,
  marcarBorrado,
  nuevoElementoLocal,
} from './elemento';

// jest-expo automockea expo-crypto: Crypto.randomUUID() devuelve undefined
// bajo Jest (sin lanzar error), asi que aqui SIEMPRE se inyecta un
// generador de id falso, igual que ya se inyecta el reloj. El generador
// real solo se ejerce en Expo Go (ver plan de verificacion sin dispositivo).
let contador = 0;
function generarIdDePrueba(): string {
  contador += 1;
  return `id-de-prueba-${contador}`;
}

beforeEach(() => {
  contador = 0;
});

describe('nuevoElementoLocal', () => {
  it('genera id y timestamps con el reloj inyectado', () => {
    const e = nuevoElementoLocal(
      { url: 'https://a.com', titulo: 'A' },
      () => 1000,
      generarIdDePrueba,
    );
    expect(e.url).toBe('https://a.com');
    expect(e.titulo).toBe('A');
    expect(e.creadoEn).toBe(1000);
    expect(e.actualizadoEn).toBe(1000);
    expect(e.borrado).toBe(false);
    expect(e.id).toBe('id-de-prueba-1');
  });

  it('genera ids distintos para dos elementos', () => {
    const a = nuevoElementoLocal({ url: 'https://a.com' }, undefined, generarIdDePrueba);
    const b = nuevoElementoLocal({ url: 'https://b.com' }, undefined, generarIdDePrueba);
    expect(a.id).not.toBe(b.id);
  });
});

describe('marcarBorrado', () => {
  it('deja un tombstone, no elimina el resto de datos', () => {
    const e = nuevoElementoLocal({ url: 'https://a.com' }, () => 100, generarIdDePrueba);
    const borrado = marcarBorrado(e, () => 200);
    expect(borrado.borrado).toBe(true);
    expect(borrado.actualizadoEn).toBe(200);
    expect(borrado.url).toBe('https://a.com');
  });
});

describe('editar', () => {
  it('cambia los campos indicados y actualiza el timestamp', () => {
    const e = nuevoElementoLocal(
      { url: 'https://a.com', titulo: 'Viejo' },
      () => 100,
      generarIdDePrueba,
    );
    const editado = editar(e, { titulo: 'Nuevo' }, () => 200);
    expect(editado.titulo).toBe('Nuevo');
    expect(editado.actualizadoEn).toBe(200);
    expect(editado.id).toBe(e.id);
  });
});

describe('elementoAJson / elementoDesdeJson', () => {
  it('son inversas', () => {
    const e = nuevoElementoLocal(
      {
        url: 'https://a.com',
        titulo: 'A',
        descripcion: 'd',
        imagenUrl: 'https://a.com/i.jpg',
        etiquetas: ['ocio', 'pendiente'],
      },
      () => 100,
      generarIdDePrueba,
    );
    const reconstruido = elementoDesdeJson(elementoAJson(e));
    expect(reconstruido).toEqual(e);
  });

  it('elementoDesdeJson admite campos ausentes', () => {
    const e = elementoDesdeJson({ id: 'x1', actualizadoEn: 5, borrado: true });
    expect(e.url).toBe('');
    expect(e.titulo).toBeNull();
    expect(e.etiquetas).toEqual([]);
    expect(e.borrado).toBe(true);
  });
});
