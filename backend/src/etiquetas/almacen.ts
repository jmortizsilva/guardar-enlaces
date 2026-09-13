import { obtenerBd } from '../db';

// Capa de datos de las etiquetas reservadas: una etiqueta que existe por si sola, sin que ningun
// elemento la lleve todavia, para poder crearla en un sitio y verla en el otro antes de usarla.
// Mismo mecanismo de sincronizacion que elementos/almacen.ts (item a item, "gana el timestamp
// mas reciente"), pero sin url: lo unico que hace falta para dar de alta una es el nombre.

export interface EtiquetaEntrada {
  id: string;
  nombre?: string;
  actualizadoEn: number;
  borrado?: boolean;
}

export interface EtiquetaDefinida {
  id: string;
  nombre: string;
  creadoEn: number;
  actualizadoEn: number;
  borrado: boolean;
}

interface FilaEtiqueta {
  id: string;
  usuario_id: number;
  nombre: string;
  creado_en: number;
  actualizado_en: number;
  borrado_en: number | null;
}

function aEtiqueta(fila: FilaEtiqueta): EtiquetaDefinida {
  return {
    id: fila.id,
    nombre: fila.nombre,
    creadoEn: fila.creado_en,
    actualizadoEn: fila.actualizado_en,
    borrado: fila.borrado_en != null,
  };
}

function filaPorId(id: string): FilaEtiqueta | undefined {
  return obtenerBd().prepare('SELECT * FROM etiquetas_definidas WHERE id = ?').get(id) as
    | FilaEtiqueta
    | undefined;
}

export interface EtiquetaRechazada {
  id: string;
  /** 'sin_nombre': era un alta y no traia nombre. 'no_aplicable': el id pertenece a otro usuario. */
  motivo: 'sin_nombre' | 'no_aplicable';
}

export interface ResultadoPushEtiquetas {
  etiquetasDefinidas: EtiquetaDefinida[];
  rechazadas: EtiquetaRechazada[];
}

export interface ResultadoPullEtiquetas {
  etiquetasDefinidas: EtiquetaDefinida[];
}

// Sin paginacion a proposito: el numero de etiquetas de una persona nunca se acerca al limite de
// lote de elementos (cientos/miles de enlaces frente a, como mucho, unas pocas decenas de
// etiquetas). Si algun dia hiciera falta, se le añadiria masDisponible igual que a elementos.
export function pull(usuarioId: number, desde: number): ResultadoPullEtiquetas {
  const filas = obtenerBd()
    .prepare(
      `SELECT * FROM etiquetas_definidas WHERE usuario_id = ? AND actualizado_en > ?
       ORDER BY actualizado_en ASC`,
    )
    .all(usuarioId, desde) as FilaEtiqueta[];
  return { etiquetasDefinidas: filas.map(aEtiqueta) };
}

const MARGEN_FUTURO_MS = 5 * 60 * 1000;

export function push(
  usuarioId: number,
  entradas: EtiquetaEntrada[],
  ahora: () => number = () => Date.now(),
): ResultadoPushEtiquetas {
  const bd = obtenerBd();
  const definitivas: EtiquetaDefinida[] = [];
  const rechazadas: EtiquetaRechazada[] = [];

  const transaccion = bd.transaction((lote: EtiquetaEntrada[]) => {
    for (const entrada of lote) {
      const existente = filaPorId(entrada.id);

      if (existente && existente.usuario_id !== usuarioId) {
        rechazadas.push({ id: entrada.id, motivo: 'no_aplicable' });
        continue;
      }

      const limiteFuturo = ahora() + MARGEN_FUTURO_MS;
      const actualizadoEn = Math.min(entrada.actualizadoEn, limiteFuturo);

      if (existente && actualizadoEn <= existente.actualizado_en) {
        definitivas.push(aEtiqueta(existente));
        continue;
      }

      const borradoEn = entrada.borrado ? actualizadoEn : null;

      if (existente) {
        bd.prepare(
          `UPDATE etiquetas_definidas SET
             nombre = COALESCE(@nombre, nombre),
             actualizado_en = @actualizadoEn,
             borrado_en = @borradoEn
           WHERE id = @id`,
        ).run({
          id: entrada.id,
          nombre: entrada.nombre ?? null,
          actualizadoEn: actualizadoEn,
          borradoEn: borradoEn,
        });
      } else {
        if (!entrada.nombre) {
          rechazadas.push({ id: entrada.id, motivo: 'sin_nombre' });
          continue;
        }
        bd.prepare(
          `INSERT INTO etiquetas_definidas
             (id, usuario_id, nombre, creado_en, actualizado_en, borrado_en)
           VALUES (@id, @usuarioId, @nombre, @creadoEn, @actualizadoEn, @borradoEn)`,
        ).run({
          id: entrada.id,
          usuarioId,
          nombre: entrada.nombre,
          creadoEn: actualizadoEn,
          actualizadoEn: actualizadoEn,
          borradoEn: borradoEn,
        });
      }

      const fila = filaPorId(entrada.id);
      if (fila) {
        definitivas.push(aEtiqueta(fila));
      }
    }
  });

  transaccion(entradas);
  return { etiquetasDefinidas: definitivas, rechazadas };
}
