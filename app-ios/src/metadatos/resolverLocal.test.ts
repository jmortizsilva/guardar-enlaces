import { extraerMetadatos } from './extraccion';
import { resolverMetadatosEnDispositivo } from './resolverLocal';

describe('extraerMetadatos', () => {
  it('prefiere og:title al <title> y decodifica entidades', () => {
    const html = `<html><head>
      <title>El de la pestaña</title>
      <meta property="og:title" content="Caf&eacute; &amp; teoría">
      <meta property="og:description" content="Una descripción">
      <meta content="https://ejemplo.com/foto.jpg" property="og:image">
      <meta property="og:type" content="article">
    </head></html>`;

    expect(extraerMetadatos(html)).toEqual({
      titulo: 'Caf&eacute; & teoría',
      descripcion: 'Una descripción',
      imagenUrl: 'https://ejemplo.com/foto.jpg',
      tipo: 'articulo',
    });
  });

  it('sin og:title cae al <title> de la pagina', () => {
    expect(extraerMetadatos('<html><head><title>  Solo esto  </title></head></html>')).toEqual({
      titulo: 'Solo esto',
      descripcion: null,
      imagenUrl: null,
      tipo: 'enlace',
    });
  });

  it('un HTML sin nada devuelve todo a null, no lanza', () => {
    expect(extraerMetadatos('<html></html>')).toEqual({
      titulo: null,
      descripcion: null,
      imagenUrl: null,
      tipo: 'enlace',
    });
  });
});

describe('resolverMetadatosEnDispositivo', () => {
  it('descarga la pagina y la lee', async () => {
    const descargar = jest
      .fn()
      .mockResolvedValue('<html><head><title>Un artículo</title></head></html>');

    const metadatos = await resolverMetadatosEnDispositivo('https://ejemplo.com/a', 100, descargar);

    expect(metadatos.titulo).toBe('Un artículo');
    expect(descargar).toHaveBeenCalledWith('https://ejemplo.com/a', 100);
  });
});
