import * as Clipboard from 'expo-clipboard';
import { router } from 'expo-router';
import { useShareIntentContext } from 'expo-share-intent';
import * as WebBrowser from 'expo-web-browser';
import { useEffect, useMemo, useState } from 'react';
import { Alert, FlatList, StyleSheet, Text, View } from 'react-native';

import { anunciarImportante } from '../../src/accesibilidad/anuncios';
import { useAcciones, useElementos } from '../../src/contexto/ProveedorApp';
import { subtituloFila, tituloFila } from '../../src/dominio/presentacion';
import { buscar, etiquetasDisponibles, filtrarPorEtiqueta } from '../../src/dominio/sincronizacion';
import { Boton } from '../../src/interfaz/Boton';
import { CampoBusqueda } from '../../src/interfaz/CampoBusqueda';
import { ChipEtiqueta } from '../../src/interfaz/ChipEtiqueta';
import { DialogoEtiquetas } from '../../src/interfaz/DialogoEtiquetas';
import { FilaLista } from '../../src/interfaz/FilaLista';
import { ESPACIADO, useTema } from '../../src/interfaz/tema';

export default function Lista() {
  const tema = useTema();
  const { elementos, sincronizando } = useElementos();
  const { eliminar, editarEtiquetas } = useAcciones();
  const { hasShareIntent, shareIntent, resetShareIntent } = useShareIntentContext();
  const [texto, setTexto] = useState('');
  const [etiqueta, setEtiqueta] = useState<string | null>(null);
  const [editandoId, setEditandoId] = useState<string | null>(null);

  useEffect(() => {
    if (hasShareIntent && shareIntent.webUrl) {
      router.push({ pathname: '/anadir', params: { urlCompartida: shareIntent.webUrl } });
      resetShareIntent();
    }
  }, [hasShareIntent, shareIntent.webUrl, resetShareIntent]);

  const etiquetasTodas = useMemo(() => etiquetasDisponibles(elementos), [elementos]);
  const visibles = useMemo(
    () => buscar(filtrarPorEtiqueta(elementos, etiqueta), texto),
    [elementos, etiqueta, texto],
  );

  async function copiarUrl(url: string): Promise<void> {
    await Clipboard.setStringAsync(url);
    anunciarImportante('URL copiada');
  }

  function confirmarEliminar(id: string, titulo: string): void {
    Alert.alert('Confirmar eliminación', `¿Eliminar «${titulo}»?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Eliminar',
        style: 'destructive',
        onPress: () => {
          eliminar(id);
          anunciarImportante('Elemento eliminado');
        },
      },
    ]);
  }

  const elementoEditando = editandoId ? elementos.find((e) => e.id === editandoId) : undefined;

  return (
    <View style={[estilos.contenedor, { backgroundColor: tema.fondo }]}>
      <View style={estilos.cabecera}>
        <Text accessibilityRole="header" style={[estilos.titulo, { color: tema.texto }]}>
          Guardar enlaces
        </Text>
        <View style={estilos.accionesCabecera}>
          <Boton etiqueta="Añadir" variante="secundario" alPulsar={() => router.push('/anadir')} />
          <Boton
            etiqueta="Ajustes"
            variante="secundario"
            alPulsar={() => router.push('/ajustes')}
          />
        </View>
      </View>

      <CampoBusqueda
        alCambiarTexto={setTexto}
        marcador="Título, URL o etiqueta"
        etiqueta="Buscar por título, URL o etiqueta"
      />
      <ChipEtiqueta
        etiquetas={etiquetasTodas}
        seleccionada={etiqueta}
        alSeleccionar={setEtiqueta}
      />

      {sincronizando ? <Text style={{ color: tema.textoSecundario }}>Sincronizando…</Text> : null}

      <FlatList
        data={visibles}
        keyExtractor={(elemento) => elemento.id}
        contentContainerStyle={estilos.lista}
        ListEmptyComponent={
          <Text style={{ color: tema.textoSecundario }}>No hay enlaces guardados todavía.</Text>
        }
        renderItem={({ item }) => (
          <FilaLista
            titulo={tituloFila(item)}
            subtitulo={subtituloFila(item)}
            accionPrincipal={{
              nombre: 'abrir',
              etiqueta: 'Abrir en modo lector',
              ejecutar: () => WebBrowser.openBrowserAsync(item.url, { readerMode: true }),
            }}
            accionesSecundarias={[
              { nombre: 'copiar', etiqueta: 'Copiar URL', ejecutar: () => copiarUrl(item.url) },
              {
                nombre: 'safari',
                etiqueta: 'Abrir en Safari',
                ejecutar: () => WebBrowser.openBrowserAsync(item.url),
              },
              {
                nombre: 'editar',
                etiqueta: 'Editar etiquetas',
                ejecutar: () => setEditandoId(item.id),
              },
              {
                nombre: 'eliminar',
                etiqueta: 'Eliminar',
                ejecutar: () => confirmarEliminar(item.id, tituloFila(item)),
                destructiva: true,
              },
            ]}
          />
        )}
      />

      {elementoEditando ? (
        <DialogoEtiquetas
          disponibles={etiquetasTodas}
          seleccionadas={elementoEditando.etiquetas}
          alAceptar={(etiquetas) => {
            editarEtiquetas(elementoEditando.id, etiquetas);
            setEditandoId(null);
            anunciarImportante('Etiquetas actualizadas');
          }}
          alCancelar={() => setEditandoId(null)}
        />
      ) : null}
    </View>
  );
}

const estilos = StyleSheet.create({
  contenedor: { flex: 1, padding: ESPACIADO.medio, gap: ESPACIADO.medio },
  cabecera: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  titulo: { fontSize: 22, fontWeight: '700' },
  accionesCabecera: { flexDirection: 'row', gap: ESPACIADO.pequeno },
  lista: { gap: ESPACIADO.pequeno },
});
