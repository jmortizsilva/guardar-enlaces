import * as SecureStore from 'expo-secure-store';

import { borrarTokenRefresco, guardarTokenRefresco, obtenerTokenRefresco } from './credenciales';

jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

const setItemAsync = SecureStore.setItemAsync as jest.Mock;
const getItemAsync = SecureStore.getItemAsync as jest.Mock;
const deleteItemAsync = SecureStore.deleteItemAsync as jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
});

it('guardarTokenRefresco guarda bajo la misma clave que luego usa obtenerTokenRefresco', async () => {
  setItemAsync.mockResolvedValue(undefined);
  await guardarTokenRefresco('abc123');

  expect(setItemAsync).toHaveBeenCalledTimes(1);
  const [clave, valor] = setItemAsync.mock.calls[0];
  expect(valor).toBe('abc123');

  getItemAsync.mockResolvedValue('abc123');
  expect(await obtenerTokenRefresco()).toBe('abc123');
  expect(getItemAsync.mock.calls[0][0]).toBe(clave);
});

it('obtenerTokenRefresco sin nada guardado devuelve null', async () => {
  getItemAsync.mockResolvedValue(null);
  expect(await obtenerTokenRefresco()).toBeNull();
});

it('borrarTokenRefresco no revienta si no habia nada guardado', async () => {
  deleteItemAsync.mockRejectedValue(new Error('no existe'));
  await expect(borrarTokenRefresco()).resolves.toBeUndefined();
});
