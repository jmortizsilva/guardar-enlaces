import { Elemento } from './elemento';
import { buscarDuplicado, mismaUrl, normalizarUrl } from './duplicados';

function elemento(id: string, url: string, borrado = false): Elemento {
  return {
    id,
    url,
    titulo: id,
    descripcion: null,
    imagenUrl: null,
    tipo: 'enlace',
    etiquetas: [],
    creadoEn: 0,
    actualizadoEn: 0,
    borrado,
  };
}

describe('normalizarUrl', () => {
  it('http y https son la misma pagina', () => {
    expect(mismaUrl('http://ejemplo.com/a', 'https://ejemplo.com/a')).toBe(true);
  });

  it('www, la barra final y el ancla no cuentan', () => {
    expect(mismaUrl('https://www.ejemplo.com/a/', 'https://ejemplo.com/a#seccion')).toBe(true);
  });

  it('los parametros de seguimiento se descartan', () => {
    expect(
      mismaUrl('https://ejemplo.com/a?utm_source=boletin&fbclid=xyz', 'https://ejemplo.com/a'),
    ).toBe(true);
  });

  it('pero los parametros que identifican el contenido si cuentan', () => {
    expect(mismaUrl('https://ejemplo.com/ver?id=1', 'https://ejemplo.com/ver?id=2')).toBe(false);
  });

  it('el orden de los parametros da igual', () => {
    expect(mismaUrl('https://ejemplo.com/?b=2&a=1', 'https://ejemplo.com/?a=1&b=2')).toBe(true);
  });

  it('la ruta distingue mayusculas: hay servidores donde no es lo mismo', () => {
    expect(mismaUrl('https://ejemplo.com/Uno', 'https://ejemplo.com/uno')).toBe(false);
  });

  it('lo que no es una URL se compara tal cual, sin inventar duplicados', () => {
    expect(normalizarUrl('  esto no es una url  ')).toBe('esto no es una url');
    expect(mismaUrl('esto no es una url', 'ni esto tampoco')).toBe(false);
  });
});

describe('buscarDuplicado', () => {
  const guardados = [
    elemento('e1', 'https://www.xataka.com/basics/alternativas-pocket'),
    elemento('e2', 'https://ejemplo.com/otro'),
  ];

  it('encuentra el que ya estaba, aunque venga escrito distinto', () => {
    const encontrado = buscarDuplicado(
      guardados,
      'http://xataka.com/basics/alternativas-pocket/?utm_source=twitter',
    );

    expect(encontrado?.id).toBe('e1');
  });

  it('una URL nueva no es duplicado de nada', () => {
    expect(buscarDuplicado(guardados, 'https://ejemplo.com/nuevo')).toBeUndefined();
  });

  it('lo borrado no cuenta: volver a guardarlo es un alta normal', () => {
    const conBorrado = [elemento('e3', 'https://ejemplo.com/ido', true)];

    expect(buscarDuplicado(conBorrado, 'https://ejemplo.com/ido')).toBeUndefined();
  });
});
