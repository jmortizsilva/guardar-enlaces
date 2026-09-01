import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ScrollView, StyleSheet, Text, TextInput } from 'react-native';

import { anunciarImportante } from '../../src/accesibilidad/anuncios';
import { useAcciones, useElementos } from '../../src/contexto/ProveedorApp';
import { TipoElemento } from '../../src/dominio/elemento';
import { etiquetasDisponibles } from '../../src/dominio/sincronizacion';
import { Boton } from '../../src/interfaz/Boton';
import { SelectorEtiquetas } from '../../src/interfaz/SelectorEtiquetas';
import { Tarjeta } from '../../src/interfaz/Tarjeta';
import { ESPACIADO, useTema } from '../../src/interfaz/tema';

interface VistaPrevia {
  url: string;
  titulo: string | null;
  descripcion: string | null;
  tipo: string;
}

/**
 * Pegar URL -> "Comprobar" (llama a /metadatos y muestra una vista previa)
 * -> "Guardar" (crea el elemento local, guardado explicito, nunca
 * automatico). Calco de dialogo_anadir.py.
 */
export default function Anadir() {
  const tema = useTema();
  const { urlCompartida } = useLocalSearchParams<{ urlCompartida?: string }>();
  const { anadir, comprobarMetadatos } = useAcciones();
  const { elementos } = useElementos();
  const [url, setUrl] = useState(urlCompartida ?? '');
  const [comprobando, setComprobando] = useState(false);
  const [vistaPrevia, setVistaPrevia] = useState<VistaPrevia | null>(null);
  const [error, setError] = useState('');
  const [etiquetas, setEtiquetas] = useState<string[]>([]);
  const etiquetasTodas = useMemo(() => etiquetasDisponibles(elementos), [elementos]);

  function alCambiarUrl(texto: string): void {
    setUrl(texto);
    // cambiar la URL invalida la vista previa ya comprobada: hay que volver a comprobar
    setVistaPrevia(null);
    setError('');
  }

  async function comprobar(): Promise<void> {
    const limpia = url.trim();
    if (!limpia.startsWith('http://') && !limpia.startsWith('https://')) {
      setError('Escribe una URL que empiece por http:// o https://');
      return;
    }

    setComprobando(true);
    setError('');
    try {
      const metadatos = await comprobarMetadatos(limpia);
      setVistaPrevia({ ...metadatos, url: limpia });
    } catch (fallo) {
      setError(fallo instanceof Error ? fallo.message : 'No se pudo comprobar la URL.');
    } finally {
      setComprobando(false);
    }
  }

  const yaComprobadaAlAbrir = useRef(false);
  useEffect(() => {
    if (urlCompartida && !yaComprobadaAlAbrir.current) {
      yaComprobadaAlAbrir.current = true;
      comprobar();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- solo al abrir con una URL compartida
  }, [urlCompartida]);

  function guardar(): void {
    if (!vistaPrevia) return;
    anadir({
      url: vistaPrevia.url,
      titulo: vistaPrevia.titulo,
      descripcion: vistaPrevia.descripcion,
      tipo: vistaPrevia.tipo as TipoElemento,
      etiquetas,
    });
    anunciarImportante('Enlace guardado');
    router.back();
  }

  return (
    <ScrollView
      contentContainerStyle={[estilos.contenedor, { backgroundColor: tema.fondo }]}
      style={{ backgroundColor: tema.fondo }}>
      <Boton etiqueta="Cancelar" variante="secundario" alPulsar={() => router.back()} />

      <Text style={[estilos.etiquetaCampo, { color: tema.texto }]}>URL</Text>
      <TextInput
        value={url}
        onChangeText={alCambiarUrl}
        placeholder="https://..."
        placeholderTextColor={tema.textoSecundario}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
        accessibilityLabel="URL del enlace a añadir"
        style={[estilos.campo, { borderColor: tema.borde, color: tema.texto }]}
      />

      <Boton
        etiqueta="Comprobar"
        alPulsar={comprobar}
        ocupado={comprobando}
        deshabilitado={!url.trim()}
      />

      {error ? <Text style={{ color: tema.peligro }}>{error}</Text> : null}

      {vistaPrevia ? (
        <Tarjeta
          etiquetaAccesible={`Vista previa: ${vistaPrevia.titulo || vistaPrevia.url}`}
          destacado={vistaPrevia.titulo || vistaPrevia.url}
          filas={
            vistaPrevia.descripcion
              ? [{ etiqueta: 'Descripción', visible: vistaPrevia.descripcion }]
              : []
          }
        />
      ) : null}

      {vistaPrevia ? (
        <>
          <Text style={[estilos.etiquetaCampo, { color: tema.texto }]}>Etiquetas</Text>
          <SelectorEtiquetas
            disponibles={etiquetasTodas}
            seleccionadas={etiquetas}
            alCambiar={setEtiquetas}
          />
        </>
      ) : null}

      <Boton etiqueta="Guardar" alPulsar={guardar} deshabilitado={!vistaPrevia} />
    </ScrollView>
  );
}

const estilos = StyleSheet.create({
  contenedor: { flexGrow: 1, padding: ESPACIADO.grande, gap: ESPACIADO.medio },
  etiquetaCampo: { fontSize: 15, fontWeight: '600' },
  campo: {
    borderWidth: 1,
    borderRadius: 8,
    padding: ESPACIADO.medio,
    fontSize: 17,
  },
});
