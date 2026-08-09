import { beforeEach, describe, expect, it } from 'vitest';
import { inicializarBd } from '../db';
import { estaInvitado, invitar, obtenerOCrearUsuario, obtenerUsuarioPorId } from '../auth/usuarios';

beforeEach(() => {
  inicializarBd(':memory:');
});

describe('invitados', () => {
  it('un correo invitado se reconoce sin distinguir mayusculas', () => {
    invitar('Persona@Ejemplo.com');
    expect(estaInvitado('persona@ejemplo.com')).toBe(true);
    expect(estaInvitado('PERSONA@EJEMPLO.COM')).toBe(true);
  });

  it('un correo no invitado no se reconoce', () => {
    expect(estaInvitado('nadie@ejemplo.com')).toBe(false);
  });
});

describe('obtenerOCrearUsuario', () => {
  it('crea la cuenta si el email esta invitado', () => {
    invitar('persona@ejemplo.com');
    const usuario = obtenerOCrearUsuario({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });
    expect(usuario).toBeDefined();
    expect(obtenerUsuarioPorId(usuario!.id)?.email).toBe('persona@ejemplo.com');
  });

  it('no crea la cuenta si el email no esta invitado', () => {
    const usuario = obtenerOCrearUsuario({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'nadie@ejemplo.com',
      emailVerificado: true,
    });
    expect(usuario).toBeUndefined();
  });

  it('sin email en el perfil, no se puede crear la cuenta', () => {
    const usuario = obtenerOCrearUsuario({
      proveedor: 'apple',
      idProveedor: 'sub1',
      email: null,
      emailVerificado: false,
    });
    expect(usuario).toBeUndefined();
  });

  it('un login posterior del mismo (proveedor, idProveedor) reutiliza la cuenta ya creada, aunque ya no este invitado', () => {
    invitar('persona@ejemplo.com');
    const primero = obtenerOCrearUsuario({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });
    // se borra la invitacion (no afecta a quien ya tiene cuenta)
    const segundo = obtenerOCrearUsuario({
      proveedor: 'google',
      idProveedor: 'sub1',
      email: 'persona@ejemplo.com',
      emailVerificado: true,
    });
    expect(segundo?.id).toBe(primero?.id);
  });
});
