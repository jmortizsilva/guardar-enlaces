import { AlmacenAsentable, asentarCuenta, identidadDueno } from './asentarCuenta';

function almacenFalso(dueno: string | null, cuantos: number): jest.Mocked<AlmacenAsentable> {
  return {
    duenoActual: jest.fn().mockReturnValue(dueno),
    fijarDueno: jest.fn(),
    contarElementos: jest.fn().mockReturnValue(cuantos),
    marcarTodosPendientes: jest.fn(),
    vaciar: jest.fn(),
    fijarCursor: jest.fn(),
  };
}

const CUENTA = identidadDueno('https://api.ejemplo.com', 'persona@ejemplo.com');
const OTRA_CUENTA = identidadDueno('https://api.ejemplo.com', 'otra@ejemplo.com');

it('entrar en la cuenta de siempre no pregunta ni toca nada', async () => {
  const almacen = almacenFalso(CUENTA, 12);
  const decidir = jest.fn();

  await asentarCuenta(almacen, CUENTA, decidir);

  expect(decidir).not.toHaveBeenCalled();
  expect(almacen.vaciar).not.toHaveBeenCalled();
  expect(almacen.marcarTodosPendientes).not.toHaveBeenCalled();
  expect(almacen.fijarCursor).not.toHaveBeenCalled();
});

it('con el telefono vacio no pregunta: no hay nada que decidir', async () => {
  const almacen = almacenFalso(null, 0);
  const decidir = jest.fn();

  await asentarCuenta(almacen, CUENTA, decidir);

  expect(decidir).not.toHaveBeenCalled();
  expect(almacen.fijarDueno).toHaveBeenCalledWith(CUENTA);
});

it('lo guardado sin cuenta se importa si el usuario dice que si', async () => {
  const almacen = almacenFalso(null, 3);
  const decidir = jest.fn().mockResolvedValue(true);

  await asentarCuenta(almacen, CUENTA, decidir);

  expect(decidir).toHaveBeenCalledWith({ cuantos: 3, deOtraCuenta: false });
  expect(almacen.marcarTodosPendientes).toHaveBeenCalled();
  expect(almacen.vaciar).not.toHaveBeenCalled();
  // Sin esto, el primer pull se saltaria lo anterior al cursor de antes.
  expect(almacen.fijarCursor).toHaveBeenCalledWith(0);
  expect(almacen.fijarDueno).toHaveBeenCalledWith(CUENTA);
});

it('si dice que no, se borra lo que habia', async () => {
  const almacen = almacenFalso(null, 3);
  const decidir = jest.fn().mockResolvedValue(false);

  await asentarCuenta(almacen, CUENTA, decidir);

  expect(almacen.vaciar).toHaveBeenCalled();
  expect(almacen.marcarTodosPendientes).not.toHaveBeenCalled();
  expect(almacen.fijarDueno).toHaveBeenCalledWith(CUENTA);
});

it('al cambiar de cuenta se avisa de que lo de antes era de otra', async () => {
  const almacen = almacenFalso(OTRA_CUENTA, 5);
  const decidir = jest.fn().mockResolvedValue(false);

  await asentarCuenta(almacen, CUENTA, decidir);

  expect(decidir).toHaveBeenCalledWith({ cuantos: 5, deOtraCuenta: true });
});

it('el mismo correo en otro servidor es otra biblioteca', () => {
  expect(identidadDueno('https://api.ejemplo.com', 'a@b.com')).not.toBe(
    identidadDueno('http://192.168.1.10:8090', 'a@b.com'),
  );
});
