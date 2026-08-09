/**
 * Logica pura del dominio: el elemento guardado.
 *
 * Sin red ni interfaz aqui (separar lo que se puede probar sin dispositivo/
 * servidor de la fontaneria). El reloj y el generador de id se inyectan para
 * que los tests sean deterministas.
 *
 * Timestamps en milisegundos (Date.now()), igual que el backend.
 */
import * as Crypto from 'expo-crypto';

export type TipoElemento = 'enlace' | 'video' | 'articulo' | 'imagen';

export interface Elemento {
  readonly id: string;
  readonly url: string;
  readonly titulo: string | null;
  readonly descripcion: string | null;
  readonly imagenUrl: string | null;
  readonly tipo: TipoElemento;
  readonly etiquetas: readonly string[];
  readonly creadoEn: number;
  readonly actualizadoEn: number;
  readonly borrado: boolean;
}

export const ahoraMs = (): number => Date.now();

export function elementoAJson(elemento: Elemento): Record<string, unknown> {
  return {
    id: elemento.id,
    url: elemento.url,
    titulo: elemento.titulo,
    descripcion: elemento.descripcion,
    imagenUrl: elemento.imagenUrl,
    tipo: elemento.tipo,
    etiquetas: [...elemento.etiquetas],
    creadoEn: elemento.creadoEn,
    actualizadoEn: elemento.actualizadoEn,
    borrado: elemento.borrado,
  };
}

export function elementoDesdeJson(datos: Record<string, unknown>): Elemento {
  return {
    id: String(datos.id),
    url: (datos.url as string) || '',
    titulo: (datos.titulo as string | null) ?? null,
    descripcion: (datos.descripcion as string | null) ?? null,
    imagenUrl: (datos.imagenUrl as string | null) ?? null,
    tipo: (datos.tipo as TipoElemento) || 'enlace',
    etiquetas: (datos.etiquetas as string[] | undefined) ?? [],
    creadoEn: (datos.creadoEn as number) ?? 0,
    actualizadoEn: (datos.actualizadoEn as number) ?? 0,
    borrado: Boolean(datos.borrado),
  };
}

export interface DatosElementoNuevo {
  url: string;
  titulo?: string | null;
  descripcion?: string | null;
  imagenUrl?: string | null;
  tipo?: TipoElemento;
  etiquetas?: readonly string[];
}

/**
 * Crea un elemento nuevo con un id generado localmente (uuid v4): permite
 * guardarlo sin conexion y hace el push idempotente (ver CONTRATO-API.md).
 */
export function nuevoElementoLocal(
  datos: DatosElementoNuevo,
  ahora: () => number = ahoraMs,
  generarId: () => string = Crypto.randomUUID,
): Elemento {
  const ts = ahora();
  return {
    id: generarId(),
    url: datos.url,
    titulo: datos.titulo ?? null,
    descripcion: datos.descripcion ?? null,
    imagenUrl: datos.imagenUrl ?? null,
    tipo: datos.tipo ?? 'enlace',
    etiquetas: datos.etiquetas ?? [],
    creadoEn: ts,
    actualizadoEn: ts,
    borrado: false,
  };
}

/**
 * Baja logica (tombstone), nunca se borra la fila de golpe: el servidor
 * necesita ver el "borrado" para avisar a los demas dispositivos.
 */
export function marcarBorrado(elemento: Elemento, ahora: () => number = ahoraMs): Elemento {
  return { ...elemento, borrado: true, actualizadoEn: ahora() };
}

/**
 * Aplica cambios de campos (titulo, etiquetas, ...) y actualiza el
 * timestamp. No se puede reeditar con esto un elemento ya borrado.
 */
export function editar(
  elemento: Elemento,
  cambios: Partial<Omit<Elemento, 'id' | 'creadoEn' | 'actualizadoEn'>>,
  ahora: () => number = ahoraMs,
): Elemento {
  return { ...elemento, ...cambios, actualizadoEn: ahora() };
}
