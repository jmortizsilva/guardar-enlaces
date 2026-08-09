import { ClienteApi, ErrorApi } from '../api/clienteApi';
import * as credenciales from './credenciales';
import { Sesion } from './sesion';

jest.mock('./credenciales');

const credencialesMock = credenciales as jest.Mocked<typeof credenciales>;

function clienteFalso(): jest.Mocked<ClienteApi> {
  return {
    devLogin: jest.fn(),
    renovar: jest.fn(),
    logout: jest.fn(),
  } as unknown as jest.Mocked<ClienteApi>;
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('iniciarConDevLogin', () => {
  it('guarda token de acceso, usuario y token de refresco', async () => {
    const cliente = clienteFalso();
    cliente.devLogin.mockResolvedValue({
      tokenAcceso: 'acc1',
      expiraEn: 0,
      tokenRefresco: 'ref1',
      usuario: { id: 1, email: 'a@b.com', proveedor: 'google' },
    });
    const sesion = new Sesion(cliente);

    await sesion.iniciarConDevLogin('a@b.com');

    expect(sesion.autenticado).toBe(true);
    expect(sesion.tokenAcceso).toBe('acc1');
    expect(sesion.usuario).toEqual({ id: 1, email: 'a@b.com', proveedor: 'google' });
    expect(credencialesMock.guardarTokenRefresco).toHaveBeenCalledWith('ref1');
  });
});

describe('restaurar', () => {
  it('sin token guardado devuelve false', async () => {
    credencialesMock.obtenerTokenRefresco.mockResolvedValue(null);
    const sesion = new Sesion(clienteFalso());

    expect(await sesion.restaurar()).toBe(false);
    expect(sesion.autenticado).toBe(false);
  });

  it('con token valido renueva la sesion', async () => {
    credencialesMock.obtenerTokenRefresco.mockResolvedValue('ref1');
    const cliente = clienteFalso();
    cliente.renovar.mockResolvedValue({ tokenAcceso: 'acc2', expiraEn: 0, tokenRefresco: 'ref2' });
    const sesion = new Sesion(cliente);

    expect(await sesion.restaurar()).toBe(true);
    expect(sesion.tokenAcceso).toBe('acc2');
    expect(credencialesMock.guardarTokenRefresco).toHaveBeenCalledWith('ref2');
  });

  it('con token caducado lo borra y devuelve false', async () => {
    credencialesMock.obtenerTokenRefresco.mockResolvedValue('ref1');
    const cliente = clienteFalso();
    cliente.renovar.mockRejectedValue(new ErrorApi('caducado', 401));
    const sesion = new Sesion(cliente);

    expect(await sesion.restaurar()).toBe(false);
    expect(credencialesMock.borrarTokenRefresco).toHaveBeenCalledTimes(1);
  });
});

describe('conReintento', () => {
  it('no hace nada especial si no hay error', async () => {
    const sesion = new Sesion(clienteFalso());
    sesion.tokenAcceso = 'acc1';

    const resultado = await sesion.conReintento(async (token) => `ok-${token}`);
    expect(resultado).toBe('ok-acc1');
  });

  it('renueva una vez tras un 401 y reintenta', async () => {
    credencialesMock.obtenerTokenRefresco.mockResolvedValue('ref-viejo');
    const cliente = clienteFalso();
    cliente.renovar.mockResolvedValue({
      tokenAcceso: 'acc-nuevo',
      expiraEn: 0,
      tokenRefresco: 'ref-nuevo',
    });
    const sesion = new Sesion(cliente);
    sesion.tokenAcceso = 'acc-caducado';

    const llamadas: string[] = [];
    const funcion = async (token: string) => {
      llamadas.push(token);
      if (token === 'acc-caducado') {
        throw new ErrorApi('caducado', 401);
      }
      return 'listo';
    };

    const resultado = await sesion.conReintento(funcion);

    expect(resultado).toBe('listo');
    expect(llamadas).toEqual(['acc-caducado', 'acc-nuevo']);
  });

  it('propaga un error que no es 401', async () => {
    const sesion = new Sesion(clienteFalso());
    sesion.tokenAcceso = 'acc1';

    await expect(
      sesion.conReintento(async () => {
        throw new ErrorApi('fallo', 500);
      }),
    ).rejects.toThrow('fallo');
  });
});

describe('cerrar', () => {
  it('borra el token y limpia el estado', async () => {
    credencialesMock.obtenerTokenRefresco.mockResolvedValue('ref1');
    const cliente = clienteFalso();
    cliente.logout.mockResolvedValue(undefined);
    const sesion = new Sesion(cliente);
    sesion.tokenAcceso = 'acc1';
    sesion.usuario = { id: 1, email: 'a@b.com', proveedor: 'google' };

    await sesion.cerrar();

    expect(cliente.logout).toHaveBeenCalledWith('ref1');
    expect(credencialesMock.borrarTokenRefresco).toHaveBeenCalledTimes(1);
    expect(sesion.tokenAcceso).toBeNull();
    expect(sesion.usuario).toBeNull();
  });
});
