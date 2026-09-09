import { beforeEach, describe, expect, it } from 'vitest';
import { inicializarBd } from '../db';
import { obtenerOCrearUsuario, obtenerUsuarioPorId } from '../auth/usuarios';

beforeEach(() => {
  inicializarBd(':memory:');
});

describe('obtenerOCrearUsuario', () => {
  it('el primer login crea la cuenta: el alta es abierta', () => {
    const usuario = obtenerOCrearUsuario({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });

    expect(usuario).toBeDefined();
    expect(obtenerUsuarioPorId(usuario!.id)?.email).toBe('persona@ejemplo.com');
  });

  it('el segundo login del mismo (proveedor, idProveedor) reutiliza la cuenta', () => {
    const primero = obtenerOCrearUsuario({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });
    const segundo = obtenerOCrearUsuario({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });

    expect(segundo?.id).toBe(primero?.id);
  });

  it('el mismo correo cambia de proveedor: la identidad es el sub, no el email', () => {
    const conGoogle = obtenerOCrearUsuario({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });
    const conApple = obtenerOCrearUsuario({
      proveedor: 'apple',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });

    expect(conApple?.id).not.toBe(conGoogle?.id);
  });

  it('sin email en el perfil no se puede crear la cuenta', () => {
    const usuario = obtenerOCrearUsuario({
      proveedor: 'apple',
      idProveedor: 'sub2',
      email: null,
      emailVerificado: false,
    });

    expect(usuario).toBeUndefined();
  });
});
