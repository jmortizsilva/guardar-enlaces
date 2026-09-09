import { ClienteApi, ErrorApi } from './clienteApi';

function respuestaFake(status = 200, cuerpo: unknown = {}): Response {
  return {
    ok: status < 400,
    status,
    text: async () => JSON.stringify(cuerpo),
    json: async () => cuerpo,
  } as unknown as Response;
}

function respuestaSinJson(status: number): Response {
  return {
    ok: status < 400,
    status,
    text: async () => '<html>error</html>',
    json: async () => {
      throw new SyntaxError('no es json');
    },
  } as unknown as Response;
}

describe('peticiones correctas', () => {
  let fetchMock: jest.Mock;
  let cliente: ClienteApi;

  beforeEach(() => {
    fetchMock = jest.fn();
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    cliente = new ClienteApi('http://localhost:8081');
  });

  it('canjear manda el codigo de canje a /auth/canjear', async () => {
    fetchMock.mockResolvedValue(respuestaFake(200, { tokenAcceso: 't' }));

    await cliente.canjear('c1');

    const [url, opciones] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8081/auth/canjear');
    expect(JSON.parse(opciones.body)).toEqual({ codigoCanje: 'c1' });
  });

  it('urlIniciarLogin arma la URL del contrato sin pedir nada', () => {
    expect(cliente.urlIniciarLogin('google', 'e1', 'guardarenlaces')).toBe(
      'http://localhost:8081/auth/iniciar?proveedor=google&modo=deeplink&estado=e1&esquema=guardarenlaces',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('devLogin llama a la ruta correcta', async () => {
    fetchMock.mockResolvedValue(respuestaFake(200, { tokenAcceso: 't' }));

    const resultado = await cliente.devLogin('a@b.com');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, opciones] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8081/auth/dev-login');
    expect(opciones.method).toBe('POST');
    expect(JSON.parse(opciones.body)).toEqual({ email: 'a@b.com' });
    expect(resultado).toEqual({ tokenAcceso: 't' });
  });

  it('pull manda el token en la cabecera Authorization y los parametros correctos', async () => {
    fetchMock.mockResolvedValue(
      respuestaFake(200, { elementos: [], servidorEn: 1, masDisponible: false }),
    );

    await cliente.pull(100, 'tok123', 50);

    const [url, opciones] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8081/sincronizar?desde=100&limite=50');
    expect(opciones.headers.Authorization).toBe('Bearer tok123');
  });

  it('push manda el lote de elementos', async () => {
    const lote = [{ id: 'e1', url: 'https://a.com', actualizadoEn: 100 }];
    fetchMock.mockResolvedValue(respuestaFake(200, { elementos: lote }));

    const resultado = await cliente.push(lote, 'tok');

    const [, opciones] = fetchMock.mock.calls[0];
    expect(JSON.parse(opciones.body)).toEqual({ elementos: lote });
    expect(resultado).toEqual({ elementos: lote });
  });

  it('urlBase con barra final no duplica la barra', async () => {
    fetchMock.mockResolvedValue(respuestaFake(200, {}));
    const clienteConBarra = new ClienteApi('http://localhost:8081/');

    await clienteConBarra.logout('tok');

    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8081/auth/logout');
  });
});

describe('errores', () => {
  let fetchMock: jest.Mock;
  let cliente: ClienteApi;

  beforeEach(() => {
    fetchMock = jest.fn();
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    cliente = new ClienteApi('http://localhost:8081');
  });

  it('un 403 lanza ErrorApi con el mensaje del servidor', async () => {
    fetchMock.mockResolvedValue(respuestaFake(403, { error: 'sesion no valida' }));

    await expect(cliente.devLogin('nadie@x.com')).rejects.toMatchObject({
      message: expect.stringContaining('sesion no valida'),
      statusCode: 403,
    });
  });

  it('un fallo de red lanza ErrorApi', async () => {
    fetchMock.mockRejectedValue(new Error('rechazado'));

    await expect(cliente.pull(0, 'tok')).rejects.toThrow(/no se pudo conectar/);
  });

  it('un error sin cuerpo json no revienta', async () => {
    fetchMock.mockResolvedValue(respuestaSinJson(500));

    await expect(cliente.logout('tok')).rejects.toMatchObject({
      message: expect.stringContaining('500'),
    });
  });

  it('ErrorApi es instancia de Error', async () => {
    fetchMock.mockResolvedValue(respuestaFake(400, { error: 'mal' }));
    try {
      await cliente.devLogin('x@x.com');
      fail('deberia haber lanzado');
    } catch (error) {
      expect(error).toBeInstanceOf(ErrorApi);
      expect(error).toBeInstanceOf(Error);
    }
  });
});
