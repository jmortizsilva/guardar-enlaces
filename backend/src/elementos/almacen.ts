import { obtenerBd } from '../db';

// Capa de datos de los elementos guardados. Sincronizacion a NIVEL DE ITEM (no reemplazo total
// como en servidor-notificaciones): cada elemento se fusiona por separado entre los dispositivos
// de un mismo usuario, con "gana el timestamp mas reciente" como resolucion de conflicto.

export interface ElementoEntrada {
  id: string;
  url?: string;
  titulo?: string | null;
  descripcion?: string | null;
  imagenUrl?: string | null;
  tipo?: string;
  etiquetas?: string[];
  actualizadoEn: number;
  borrado?: boolean;
}

export interface Elemento {
  id: string;
  url: string;
  titulo: string | null;
  descripcion: string | null;
  imagenUrl: string | null;
  tipo: string;
  etiquetas: string[];
  creadoEn: number;
  actualizadoEn: number;
  borrado: boolean;
}

interface FilaElemento {
  id: string;
  usuario_id: number;
  url: string;
  titulo: string | null;
  descripcion: string | null;
  imagen_url: string | null;
  tipo: string;
  etiquetas: string | null;
  creado_en: number;
  actualizado_en: number;
  borrado_en: number | null;
}

function aElemento(fila: FilaElemento): Elemento {
  let etiquetas: string[] = [];
  if (fila.etiquetas) {
    try {
      etiquetas = JSON.parse(fila.etiquetas);
    } catch {
      etiquetas = [];
    }
  }
  return {
    id: fila.id,
    url: fila.url,
    titulo: fila.titulo,
    descripcion: fila.descripcion,
    imagenUrl: fila.imagen_url,
    tipo: fila.tipo,
    etiquetas,
    creadoEn: fila.creado_en,
    actualizadoEn: fila.actualizado_en,
    borrado: fila.borrado_en != null,
  };
}

function filaPorId(id: string): FilaElemento | undefined {
  return obtenerBd().prepare('SELECT * FROM elementos WHERE id = ?').get(id) as
    | FilaElemento
    | undefined;
}

export interface EntradaRechazada {
  id: string;
  /**
   * 'sin_url': era un alta y no traia url, no hay nada que crear.
   * 'no_aplicable': el servidor no puede aplicar ese cambio. NO se detalla mas
   * a proposito: el unico caso real es que el id pertenezca a otro usuario, y
   * decirlo confirmaria que existe.
   */
  motivo: 'sin_url' | 'no_aplicable';
}

export interface ResultadoPush {
  /** Version definitiva de lo aplicado, para que el cliente corrija su cache. */
  elementos: Elemento[];
  /**
   * Lo que el servidor NO ha aplicado. Va aparte a proposito: antes se
   * descartaba en silencio devolviendo 200, y el cliente --que vacia su buzon
   * de salida con los ids que le vuelven-- reintentaba esas entradas en cada
   * sincronizacion, para siempre y sin que nadie se enterara.
   */
  rechazados: EntradaRechazada[];
}

export interface ResultadoPull {
  elementos: Elemento[];
  servidorEn: number;
  masDisponible: boolean;
}

// Pull incremental: todo lo que cambio para este usuario despues de `desde` (timestamp en ms).
// `desde=0` trae toda la biblioteca. Pide un elemento de mas para saber si hay que paginar.
export function pull(
  usuarioId: number,
  desde: number,
  limite: number,
  ahora: () => number = () => Date.now(),
): ResultadoPull {
  const servidorEn = ahora();
  const filas = obtenerBd()
    .prepare(
      `SELECT * FROM elementos WHERE usuario_id = ? AND actualizado_en > ?
       ORDER BY actualizado_en ASC LIMIT ?`,
    )
    .all(usuarioId, desde, limite + 1) as FilaElemento[];

  const masDisponible = filas.length > limite;
  const filasDevueltas = masDisponible ? filas.slice(0, limite) : filas;
  return {
    elementos: filasDevueltas.map(aElemento),
    servidorEn,
    masDisponible,
  };
}

// Margen de tolerancia ante relojes de cliente adelantados: si un dispositivo manda un
// actualizadoEn muy en el futuro (reloj mal puesto), se recorta al reloj del servidor para que no
// "gane" indefinidamente todos los conflictos futuros.
const MARGEN_FUTURO_MS = 5 * 60 * 1000;

// Aplica un lote de altas/ediciones/bajas de un usuario. Devuelve la version definitiva de cada
// elemento del lote tras resolver conflictos (para que el cliente corrija su cache si perdio).
export function push(
  usuarioId: number,
  entradas: ElementoEntrada[],
  ahora: () => number = () => Date.now(),
): ResultadoPush {
  const bd = obtenerBd();
  const definitivos: Elemento[] = [];
  const rechazados: EntradaRechazada[] = [];

  const transaccion = bd.transaction((lote: ElementoEntrada[]) => {
    for (const entrada of lote) {
      const existente = filaPorId(entrada.id);

      if (existente && existente.usuario_id !== usuarioId) {
        // El id ya es de OTRO usuario. Deja de ser un caso imposible desde que un cliente
        // puede importar a una cuenta nueva los enlaces que tenia guardados con otra: si
        // conserva los ids, todos chocan. El cliente los renumera antes de importarlos,
        // pero si aun asi llega uno, se rechaza y se dice, sin revelar de quien es.
        rechazados.push({ id: entrada.id, motivo: 'no_aplicable' });
        continue;
      }

      const limiteFuturo = ahora() + MARGEN_FUTURO_MS;
      const actualizadoEn = Math.min(entrada.actualizadoEn, limiteFuturo);

      if (existente && actualizadoEn <= existente.actualizado_en) {
        // El servidor ya tiene algo igual o mas reciente: gana el servidor, se ignora el cambio.
        definitivos.push(aElemento(existente));
        continue;
      }

      const etiquetasJson = JSON.stringify(entrada.etiquetas ?? []);
      const borradoEn = entrada.borrado ? actualizadoEn : null;

      if (existente) {
        bd.prepare(
          `UPDATE elementos SET
             url = COALESCE(@url, url),
             titulo = COALESCE(@titulo, titulo),
             descripcion = COALESCE(@descripcion, descripcion),
             imagen_url = COALESCE(@imagenUrl, imagen_url),
             tipo = COALESCE(@tipo, tipo),
             etiquetas = @etiquetas,
             actualizado_en = @actualizadoEn,
             borrado_en = @borradoEn
           WHERE id = @id`,
        ).run({
          id: entrada.id,
          url: entrada.url ?? null,
          titulo: entrada.titulo ?? null,
          descripcion: entrada.descripcion ?? null,
          imagenUrl: entrada.imagenUrl ?? null,
          tipo: entrada.tipo ?? null,
          etiquetas: etiquetasJson,
          actualizadoEn: actualizadoEn,
          borradoEn: borradoEn,
        });
      } else {
        if (!entrada.url) {
          // Alta sin url: no hay nada que crear.
          rechazados.push({ id: entrada.id, motivo: 'sin_url' });
          continue;
        }
        bd.prepare(
          `INSERT INTO elementos
             (id, usuario_id, url, titulo, descripcion, imagen_url, tipo, etiquetas,
              creado_en, actualizado_en, borrado_en)
           VALUES (@id, @usuarioId, @url, @titulo, @descripcion, @imagenUrl, @tipo, @etiquetas,
                   @creadoEn, @actualizadoEn, @borradoEn)`,
        ).run({
          id: entrada.id,
          usuarioId,
          url: entrada.url,
          titulo: entrada.titulo ?? null,
          descripcion: entrada.descripcion ?? null,
          imagenUrl: entrada.imagenUrl ?? null,
          tipo: entrada.tipo ?? 'enlace',
          etiquetas: etiquetasJson,
          creadoEn: actualizadoEn,
          actualizadoEn: actualizadoEn,
          borradoEn: borradoEn,
        });
      }

      const fila = filaPorId(entrada.id);
      if (fila) {
        definitivos.push(aElemento(fila));
      }
    }
  });

  transaccion(entradas);
  return { elementos: definitivos, rechazados };
}
