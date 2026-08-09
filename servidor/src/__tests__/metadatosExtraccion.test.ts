import { describe, expect, it } from 'vitest';
import { extraerMetadatos } from '../metadatos/extraccion';

describe('extraerMetadatos', () => {
  it('lee los meta og:* estandar', () => {
    const html = `<html><head>
      <meta property="og:title" content="Un articulo" />
      <meta property="og:description" content="Una descripcion" />
      <meta property="og:image" content="https://a.com/img.jpg" />
      <meta property="og:type" content="article" />
    </head></html>`;
    expect(extraerMetadatos(html)).toEqual({
      titulo: 'Un articulo',
      descripcion: 'Una descripcion',
      imagenUrl: 'https://a.com/img.jpg',
      tipo: 'articulo',
    });
  });

  it('admite el orden content/property invertido', () => {
    const html = `<meta content="Titulo invertido" property="og:title" />`;
    expect(extraerMetadatos(html).titulo).toBe('Titulo invertido');
  });

  it('sin og:title, usa <title> como respaldo', () => {
    const html = `<html><head><title>Titulo de la pagina</title></head></html>`;
    const meta = extraerMetadatos(html);
    expect(meta.titulo).toBe('Titulo de la pagina');
    expect(meta.descripcion).toBeNull();
    expect(meta.tipo).toBe('enlace'); // sin og:type -> por defecto
  });

  it('decodifica entidades HTML basicas', () => {
    const html = `<meta property="og:title" content="Café &amp; T&#39;e" />`;
    expect(extraerMetadatos(html).titulo).toBe("Café & T'e");
  });

  it('sin nada de metadatos, todo es null salvo el tipo por defecto', () => {
    const meta = extraerMetadatos('<html><body>vacio</body></html>');
    expect(meta).toEqual({ titulo: null, descripcion: null, imagenUrl: null, tipo: 'enlace' });
  });

  it('clasifica el tipo segun og:type', () => {
    expect(extraerMetadatos('<meta property="og:type" content="video.movie" />').tipo).toBe(
      'video',
    );
    expect(extraerMetadatos('<meta property="og:type" content="image" />').tipo).toBe('imagen');
  });
});
