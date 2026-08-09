// Extraccion de metadatos Open Graph de un HTML ya descargado. Logica pura (sin red, sin
// dependencias de parseo HTML como cheerio): un par de expresiones regulares tolerantes bastan
// para el caso comun. Si en la practica resultan fragiles con HTML real, se puede anadir cheerio
// mas adelante sin cambiar el contrato de esta funcion.

export interface MetadatosExtraidos {
  titulo: string | null;
  descripcion: string | null;
  imagenUrl: string | null;
  tipo: string;
}

function decodificarEntidades(texto: string): string {
  return texto
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}

// El orden property/content (o content/property) varia entre sitios; se prueban ambos.
function metaOg(html: string, propiedad: string): string | null {
  const patrones = [
    new RegExp(`<meta[^>]+property=["']og:${propiedad}["'][^>]+content=["']([^"']*)["']`, 'i'),
    new RegExp(`<meta[^>]+content=["']([^"']*)["'][^>]+property=["']og:${propiedad}["']`, 'i'),
  ];
  for (const patron of patrones) {
    const coincidencia = html.match(patron);
    if (coincidencia) {
      return decodificarEntidades(coincidencia[1]);
    }
  }
  return null;
}

function tituloFallback(html: string): string | null {
  const coincidencia = html.match(/<title[^>]*>([^<]*)<\/title>/i);
  return coincidencia ? decodificarEntidades(coincidencia[1].trim()) || null : null;
}

function tipoDesdeOg(ogType: string | null): string {
  if (!ogType) {
    return 'enlace';
  }
  if (ogType.includes('video')) {
    return 'video';
  }
  if (ogType.includes('article')) {
    return 'articulo';
  }
  if (ogType.includes('image') || ogType.includes('photo')) {
    return 'imagen';
  }
  return 'enlace';
}

export function extraerMetadatos(html: string): MetadatosExtraidos {
  return {
    titulo: metaOg(html, 'title') ?? tituloFallback(html),
    descripcion: metaOg(html, 'description'),
    imagenUrl: metaOg(html, 'image'),
    tipo: tipoDesdeOg(metaOg(html, 'type')),
  };
}
