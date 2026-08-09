import { editar, Elemento, nuevoElementoLocal } from './elemento';
import {
  aplicarPull,
  aplicarRespuestaPush,
  buscar,
  Cache,
  elementosVisibles,
  etiquetasDisponibles,
  filtrarPorEtiqueta,
} from './sincronizacion';

// jest-expo automockea expo-crypto: Crypto.randomUUID() devuelve undefined
// bajo Jest, asi que aqui SIEMPRE se inyecta un generador de id falso (igual
// que ya se inyecta el reloj) — si no, todos los elementos comparten la
// misma clave "undefined" en la cache y los tests dejan de probar nada real
// (ver elemento.test.ts para el mismo comentario, mas detallado).
let contador = 0;
function generarIdDePrueba(): string {
  contador += 1;
  return `id-de-prueba-${contador}`;
}

beforeEach(() => {
  contador = 0;
});

function nuevo(datos: Parameters<typeof nuevoElementoLocal>[0], ahora?: () => number): Elemento {
  return nuevoElementoLocal(datos, ahora, generarIdDePrueba);
}

function conTituloYFecha(elemento: Elemento, titulo: string, actualizadoEn: number): Elemento {
  return { ...elemento, titulo, actualizadoEn };
}

describe('aplicarPull', () => {
  it('agrega elementos nuevos a la cache', () => {
    const recibido = nuevo({ url: 'https://a.com' }, () => 100);
    const cache = aplicarPull({}, [recibido], {});
    expect(cache[recibido.id]).toEqual(recibido);
  });

  it('no pisa un cambio local mas reciente que el pull', () => {
    const base = nuevo({ url: 'https://a.com', titulo: 'Original' }, () => 100);
    const pendienteLocal = editar(base, { titulo: 'Editado en este dispositivo' }, () => 300);
    const delServidor = conTituloYFecha(base, 'Version antigua del servidor', 200);

    const cache: Cache = aplicarPull({ [base.id]: base }, [delServidor], {
      [base.id]: pendienteLocal,
    });
    expect(cache[base.id]).toEqual(base);
  });

  it('si el pull es mas nuevo que lo pendiente, si se aplica', () => {
    const base = nuevo({ url: 'https://a.com', titulo: 'Original' }, () => 100);
    const pendienteLocal = editar(base, { titulo: 'Editado en este dispositivo' }, () => 150);
    const delServidor = conTituloYFecha(base, 'Ya sincronizado desde otro dispositivo', 500);

    const cache: Cache = aplicarPull({ [base.id]: base }, [delServidor], {
      [base.id]: pendienteLocal,
    });
    expect(cache[base.id]).toEqual(delServidor);
  });
});

describe('aplicarRespuestaPush', () => {
  it('sustituye por la version definitiva del servidor', () => {
    const local = nuevo({ url: 'https://a.com', titulo: 'Mio' }, () => 100);
    const definitivo = conTituloYFecha(local, 'El del servidor gano el conflicto', 50);

    const cache = aplicarRespuestaPush({ [local.id]: local }, [definitivo]);
    expect(cache[local.id]).toEqual(definitivo);
  });
});

describe('elementosVisibles', () => {
  it('oculta los borrados y ordena mas reciente primero', () => {
    const a = nuevo({ url: 'https://a.com' }, () => 100);
    const b = nuevo({ url: 'https://b.com' }, () => 200);
    const cSinBorrar = nuevo({ url: 'https://c.com' }, () => 300);
    const c = { ...cSinBorrar, borrado: true, actualizadoEn: 400 };

    const visibles = elementosVisibles({ [a.id]: a, [b.id]: b, [c.id]: c });
    expect(visibles.map((e) => e.id)).toEqual([b.id, a.id]);
  });
});

describe('buscar', () => {
  it('filtra por titulo, url o etiqueta sin distinguir mayusculas', () => {
    const a = nuevo({ url: 'https://python.org', titulo: 'Documentacion Python' });
    const b = nuevo({ url: 'https://otra.com', titulo: 'Otra cosa', etiquetas: ['python'] });
    const c = nuevo({ url: 'https://nada.com', titulo: 'Nada que ver' });

    expect(buscar([a, b, c], 'PYTHON')).toEqual([a, b]);
    expect(buscar([a, b, c], '')).toEqual([a, b, c]);
    expect(buscar([a, b, c], 'no-existe')).toEqual([]);
  });
});

describe('etiquetasDisponibles', () => {
  it('devuelve las etiquetas distintas ordenadas', () => {
    const a = nuevo({ url: 'https://a.com', etiquetas: ['ocio', 'pendiente'] });
    const b = nuevo({ url: 'https://b.com', etiquetas: ['trabajo', 'ocio'] });
    const c = nuevo({ url: 'https://c.com' });

    expect(etiquetasDisponibles([a, b, c])).toEqual(['ocio', 'pendiente', 'trabajo']);
  });

  it('sin elementos o sin etiquetas devuelve vacio', () => {
    expect(etiquetasDisponibles([])).toEqual([]);
    expect(etiquetasDisponibles([nuevo({ url: 'https://a.com' })])).toEqual([]);
  });
});

describe('filtrarPorEtiqueta', () => {
  it('filtra los que tienen la etiqueta', () => {
    const a = nuevo({ url: 'https://a.com', etiquetas: ['ocio'] });
    const b = nuevo({ url: 'https://b.com', etiquetas: ['trabajo'] });

    expect(filtrarPorEtiqueta([a, b], 'ocio')).toEqual([a]);
  });

  it('sin etiqueta seleccionada no filtra', () => {
    const a = nuevo({ url: 'https://a.com', etiquetas: ['ocio'] });
    const b = nuevo({ url: 'https://b.com' });

    expect(filtrarPorEtiqueta([a, b], null)).toEqual([a, b]);
    expect(filtrarPorEtiqueta([a, b], '')).toEqual([a, b]);
  });
});
