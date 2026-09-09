import { generarEstado, leerCallback, mensajeDeError, pedirCodigoCanje } from './loginProveedor';

// OJO: aqui el URL global es el de Node, no el subconjunto de React Native que
// corre en el telefono (ver el comentario de leerCallback). Estas pruebas
// cubren que se lea el parametro correcto, no que RN sepa parsear la URL.

describe('leerCallback', () => {
  it('con codigo devuelve exito', () => {
    expect(leerCallback('guardarenlaces://auth-callback?codigo=abc-123_XYZ')).toEqual({
      estado: 'exito',
      codigoCanje: 'abc-123_XYZ',
    });
  });

  it('sin invitacion explica que hay que pedir acceso', () => {
    const resultado = leerCallback('guardarenlaces://auth-callback?error=sin_invitacion');

    expect(resultado.estado).toBe('error');
    expect(resultado).toEqual({ estado: 'error', mensaje: mensajeDeError('sin_invitacion') });
  });

  it('sin codigo ni error conocido cae en el mensaje generico', () => {
    expect(leerCallback('guardarenlaces://auth-callback')).toEqual({
      estado: 'error',
      mensaje: 'No se pudo iniciar sesión.',
    });
  });
});

describe('generarEstado', () => {
  it('devuelve los bytes aleatorios en hexadecimal', async () => {
    const aleatorio = jest.fn().mockResolvedValue(Uint8Array.from([0, 15, 16, 255]));

    expect(await generarEstado(aleatorio)).toBe('000f10ff');
    expect(aleatorio).toHaveBeenCalledWith(16);
  });
});

describe('pedirCodigoCanje', () => {
  it('cerrar la hoja sin autorizar no es un error', async () => {
    const abrir = jest.fn().mockResolvedValue({ type: 'cancel' });

    expect(await pedirCodigoCanje('https://api/auth/iniciar', abrir)).toEqual({
      estado: 'cancelado',
    });
  });

  it('pasa el esquema de vuelta y lee el codigo del redirect', async () => {
    const abrir = jest
      .fn()
      .mockResolvedValue({ type: 'success', url: 'guardarenlaces://auth-callback?codigo=c1' });

    const resultado = await pedirCodigoCanje('https://api/auth/iniciar', abrir);

    expect(resultado).toEqual({ estado: 'exito', codigoCanje: 'c1' });
    expect(abrir).toHaveBeenCalledWith(
      'https://api/auth/iniciar',
      'guardarenlaces://auth-callback',
    );
  });
});
