/**
 * Que hacer con lo que ya hay en el telefono cuando alguien entra en una
 * cuenta. Logica pura sobre el almacen: se prueba con un almacen falso, sin
 * SQLite ni React.
 */

/** Lo que asentarCuenta necesita del almacen; AlmacenLocal lo cumple por estructura. */
export interface AlmacenAsentable {
  duenoActual(): string | null;
  fijarDueno(valor: string): void;
  contarElementos(): number;
  marcarTodosPendientes(): void;
  vaciar(): void;
  fijarCursor(valor: number): void;
}

export interface EnlacesEnElTelefono {
  cuantos: number;
  /** true si son de otra cuenta; false si se guardaron sin cuenta (modo local). */
  deOtraCuenta: boolean;
}

/** Lo pregunta la pantalla de login: la decision es del usuario, no de aqui. */
export type DecidirImportacion = (enlaces: EnlacesEnElTelefono) => Promise<boolean>;

/**
 * El "dueno" de la cache local. Lleva la URL del servidor ademas del correo: el
 * mismo correo en el servidor de pruebas y en el real son dos bibliotecas
 * distintas, y mezclarlas seria peor que empezar de cero.
 */
export function identidadDueno(urlServidor: string, email: string): string {
  return `${urlServidor}|${email}`;
}

/**
 * La cache local NO esta separada por cuenta, asi que al entrar hay que
 * resolver de quien es lo que hay: o se importa a la cuenta nueva, o se borra.
 * Se pregunta en vez de decidirlo aqui, porque las dos respuestas son
 * razonables y ninguna se puede deshacer.
 *
 * Entrar en la cuenta de siempre no pregunta ni toca nada, que es el caso
 * normal; la pregunta solo aparece al estrenar cuenta o al cambiar de una a
 * otra, y solo si hay algo que decidir.
 */
export async function asentarCuenta(
  almacen: AlmacenAsentable,
  dueno: string,
  decidirImportacion: DecidirImportacion,
): Promise<void> {
  const duenoAnterior = almacen.duenoActual();
  if (duenoAnterior === dueno) {
    return;
  }

  const cuantos = almacen.contarElementos();
  const importar =
    cuantos > 0 && (await decidirImportacion({ cuantos, deOtraCuenta: duenoAnterior !== null }));

  if (importar) {
    almacen.marcarTodosPendientes();
  } else {
    almacen.vaciar();
  }
  // El cursor era del OTRO servidor: si no se pone a cero, el primer pull pide
  // "lo cambiado desde" una fecha que en este no significa nada, y se salta
  // todo lo anterior a ella. vaciar() ya lo borra; esto cubre el otro camino.
  almacen.fijarCursor(0);
  almacen.fijarDueno(dueno);
}
