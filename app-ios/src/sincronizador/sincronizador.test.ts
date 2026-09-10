import { ClienteApi } from '../api/clienteApi';
import { elementoAJson, nuevoElementoLocal } from '../dominio/elemento';
import { Sesion } from '../sesion/sesion';
import { AlmacenEnMemoria } from '../almacen/almacenEnMemoria';
import { Sincronizador } from './sincronizador';

// jest-expo automockea expo-crypto (ver dominio/elemento.test.ts): se
// inyecta siempre un generador de id falso.
let contador = 0;
function generarIdDePrueba(): string {
  contador += 1;
  return `id-de-prueba-${contador}`;
}

beforeEach(() => {
  contador = 0;
});

function clienteFalso(): jest.Mocked<ClienteApi> {
  return {
    pull: jest.fn(),
    push: jest.fn(),
  } as unknown as jest.Mocked<ClienteApi>;
}

/** Igual que el MagicMock de sesion en test_sincronizador.py: con_reintento solo invoca la funcion. */
function sesionFalsa(): Sesion {
  return {
    conReintento: (funcion: (token: string) => Promise<unknown>) => funcion('token-de-prueba'),
  } as unknown as Sesion;
}

describe('sincronizar', () => {
  it('pull vacio no sube nada y fija el cursor', async () => {
    const almacen = new AlmacenEnMemoria();
    const cliente = clienteFalso();
    cliente.pull.mockResolvedValue({ elementos: [], servidorEn: 12345, masDisponible: false });

    await new Sincronizador(almacen, cliente, sesionFalsa()).sincronizar();

    expect(cliente.push).not.toHaveBeenCalled();
    expect(almacen.cursor()).toBe(12345);
  });

  it('baja elementos nuevos del pull', async () => {
    const almacen = new AlmacenEnMemoria();
    const cliente = clienteFalso();
    const nuevo = {
      id: 'e1',
      url: 'https://a.com',
      titulo: 'A',
      actualizadoEn: 100,
      creadoEn: 100,
      borrado: false,
    };
    cliente.pull.mockResolvedValue({ elementos: [nuevo], servidorEn: 200, masDisponible: false });

    await new Sincronizador(almacen, cliente, sesionFalsa()).sincronizar();

    expect(almacen.cargarTodos().e1.titulo).toBe('A');
    expect(almacen.cursor()).toBe(200);
  });

  it('pagina el pull mientras masDisponible sea true', async () => {
    const almacen = new AlmacenEnMemoria();
    const cliente = clienteFalso();
    const pagina1 = {
      elementos: [
        { id: 'e1', url: 'https://a.com', actualizadoEn: 100, creadoEn: 100, borrado: false },
      ],
      servidorEn: 999,
      masDisponible: true,
    };
    const pagina2 = {
      elementos: [
        { id: 'e2', url: 'https://b.com', actualizadoEn: 200, creadoEn: 200, borrado: false },
      ],
      servidorEn: 999,
      masDisponible: false,
    };
    cliente.pull.mockResolvedValueOnce(pagina1).mockResolvedValueOnce(pagina2);

    await new Sincronizador(almacen, cliente, sesionFalsa()).sincronizar();

    expect(Object.keys(almacen.cargarTodos()).sort()).toEqual(['e1', 'e2']);
    expect(cliente.pull).toHaveBeenCalledTimes(2);
    // la segunda llamada pide "desde" = el actualizadoEn de la primera pagina, no 0
    expect(cliente.pull.mock.calls[1][0]).toBe(100);
  });

  it('sube lo pendiente antes de bajar y limpia el outbox', async () => {
    const almacen = new AlmacenEnMemoria();
    const cliente = clienteFalso();
    const local = nuevoElementoLocal(
      { url: 'https://mio.com', titulo: 'Mio' },
      () => 50,
      generarIdDePrueba,
    );
    almacen.marcarPendiente(local);

    const definitivo = { ...elementoAJson(local), actualizadoEn: 50 };
    cliente.push.mockResolvedValue({ elementos: [definitivo], rechazados: [] });
    cliente.pull.mockResolvedValue({ elementos: [], servidorEn: 500, masDisponible: false });

    await new Sincronizador(almacen, cliente, sesionFalsa()).sincronizar();

    expect(cliente.push).toHaveBeenCalledTimes(1);
    expect(almacen.cargarPendientes()).toEqual({});
    expect(almacen.cargarTodos()[local.id].titulo).toBe('Mio');
  });

  it('lo que el servidor rechaza tambien sale del outbox, o se reenvia para siempre', async () => {
    const almacen = new AlmacenEnMemoria();
    const cliente = clienteFalso();
    const local = nuevoElementoLocal(
      { url: 'https://mio.com', titulo: 'Mio' },
      () => 50,
      generarIdDePrueba,
    );
    almacen.marcarPendiente(local);

    // El servidor no lo aplica y no lo devuelve en "elementos": si solo se
    // limpiara con esos, el elemento se quedaria pendiente eternamente.
    cliente.push.mockResolvedValue({
      elementos: [],
      rechazados: [{ id: local.id, motivo: 'no_aplicable' }],
    });
    cliente.pull.mockResolvedValue({ elementos: [], servidorEn: 500, masDisponible: false });

    const rechazados = await new Sincronizador(almacen, cliente, sesionFalsa()).sincronizar();

    expect(rechazados).toBe(1);
    expect(almacen.cargarPendientes()).toEqual({});
    // El enlace no se pierde de la cache local, solo deja de reintentarse.
    expect(almacen.cargarTodos()[local.id]).toBeDefined();
  });
});
