import { nuevoElementoLocal } from './elemento';
import { subtituloFila, tituloFila } from './presentacion';

describe('tituloFila', () => {
  it('usa el titulo cuando existe', () => {
    const e = nuevoElementoLocal({ url: 'https://a.com', titulo: 'A' });
    expect(tituloFila(e)).toBe('A');
  });

  it('sin titulo usa la url', () => {
    const e = nuevoElementoLocal({ url: 'https://a.com' });
    expect(tituloFila(e)).toBe('https://a.com');
  });
});

// Mediodia UTC (2024-03-15T12:00:00Z): no cambia de dia en ninguna zona
// horaria real, a diferencia de un timestamp cercano a medianoche UTC.
const MEDIODIA_UTC = Date.UTC(2024, 2, 15, 12, 0, 0);

describe('subtituloFila', () => {
  it('incluye dominio, etiquetas y fecha', () => {
    const e = nuevoElementoLocal(
      {
        url: 'https://www.ejemplo.com/articulo',
        titulo: 'Un articulo interesante',
        etiquetas: ['ocio', 'pendiente'],
      },
      () => MEDIODIA_UTC,
    );
    expect(subtituloFila(e)).toBe('www.ejemplo.com — ocio, pendiente — 15 de marzo de 2024');
  });

  it('sin titulo el subtitulo sigue mostrando el dominio', () => {
    const e = nuevoElementoLocal({ url: 'https://a.com' }, () => MEDIODIA_UTC);
    expect(subtituloFila(e).startsWith('a.com')).toBe(true);
  });

  it('sin etiquetas no deja un separador vacio', () => {
    const e = nuevoElementoLocal({ url: 'https://a.com', titulo: 'A' }, () => MEDIODIA_UTC);
    expect(subtituloFila(e)).not.toContain(' —  — ');
  });

  it('sin timestamp indica que no hay fecha', () => {
    const e = nuevoElementoLocal({ url: 'https://a.com', titulo: 'A' }, () => 0);
    expect(subtituloFila(e)).toContain('sin fecha');
  });
});
