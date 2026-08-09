import { describe, expect, it } from 'vitest';
import { emitirTokenAcceso, verificarTokenAcceso } from '../auth/tokenAcceso';

const SECRETO = 'secreto-de-prueba';

describe('tokenAcceso', () => {
  it('emite un token que se verifica correctamente', () => {
    const ahora = () => 1_000_000;
    const token = emitirTokenAcceso(42, 1_000_000 + 60_000, SECRETO);
    expect(verificarTokenAcceso(token, SECRETO, ahora)).toBe(42);
  });

  it('rechaza un token caducado', () => {
    const token = emitirTokenAcceso(42, 1_000_000, SECRETO);
    expect(verificarTokenAcceso(token, SECRETO, () => 1_000_001)).toBeUndefined();
  });

  it('rechaza un token firmado con otro secreto', () => {
    const token = emitirTokenAcceso(42, 2_000_000, SECRETO);
    expect(verificarTokenAcceso(token, 'otro-secreto', () => 1_000_000)).toBeUndefined();
  });

  it('rechaza un token manipulado (usuarioId cambiado sin volver a firmar)', () => {
    const token = emitirTokenAcceso(42, 2_000_000, SECRETO);
    const manipulado = token.replace('42', '43');
    expect(verificarTokenAcceso(manipulado, SECRETO, () => 1_000_000)).toBeUndefined();
  });

  it('rechaza formato invalido', () => {
    expect(verificarTokenAcceso('no-es-un-token', SECRETO)).toBeUndefined();
  });
});
