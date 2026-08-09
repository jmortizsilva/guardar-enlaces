import * as WebBrowser from 'expo-web-browser';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, ScrollView, Text, View } from 'react-native';

import { anunciarImportante } from '../../../src/accesibilidad/anuncios';
import { useAcciones, useElementos } from '../../../src/contexto/ProveedorApp';
import { tituloFila } from '../../../src/dominio/presentacion';
import { Boton } from '../../../src/interfaz/Boton';
import { DialogoTexto } from '../../../src/interfaz/DialogoTexto';
import { Tarjeta } from '../../../src/interfaz/Tarjeta';
import { ESPACIADO, useTema } from '../../../src/interfaz/tema';

/**
 * Al aparecer, abre ya la vista previa en modo lector (SFSafariViewController
 * via expo-web-browser, entersReaderIfAvailable). Debajo, el detalle con
 * "Abrir en Safari" (sin forzar el modo lector), "Editar etiquetas" y
 * "Eliminar" (con confirmacion). Calco de dialogo_detalle.py.
 */
export default function Detalle() {
  const tema = useTema();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { elementos } = useElementos();
  const { eliminar, editarEtiquetas } = useAcciones();
  const [editando, setEditando] = useState(false);

  const elemento = elementos.find((e) => e.id === id);

  useEffect(() => {
    if (elemento) {
      WebBrowser.openBrowserAsync(elemento.url, { readerMode: true });
    }
    // Solo al entrar en la pantalla, no cada vez que cambie el elemento (p. ej. tras editar etiquetas).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  if (!elemento) {
    return (
      <View style={{ flex: 1, padding: ESPACIADO.grande, backgroundColor: tema.fondo }}>
        <Text style={{ color: tema.texto }}>Este elemento ya no existe.</Text>
      </View>
    );
  }

  function confirmarEliminar(): void {
    Alert.alert(
      'Confirmar eliminación',
      `¿Eliminar «${tituloFila(elemento!)}»? Esta acción no se puede deshacer.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Eliminar',
          style: 'destructive',
          onPress: () => {
            eliminar(elemento!.id);
            anunciarImportante('Elemento eliminado');
            router.back();
          },
        },
      ],
    );
  }

  const filas = [{ etiqueta: 'URL', visible: elemento.url }];
  if (elemento.descripcion) {
    filas.push({ etiqueta: 'Descripción', visible: elemento.descripcion });
  }
  if (elemento.etiquetas.length > 0) {
    filas.push({ etiqueta: 'Etiquetas', visible: elemento.etiquetas.join(', ') });
  }

  return (
    <ScrollView
      contentContainerStyle={{ padding: ESPACIADO.grande, gap: ESPACIADO.medio }}
      style={{ backgroundColor: tema.fondo }}>
      <Tarjeta
        etiquetaAccesible={`${tituloFila(elemento)}. ${elemento.url}`}
        destacado={tituloFila(elemento)}
        filas={filas}
      />

      <Boton
        etiqueta="Abrir en Safari"
        variante="secundario"
        alPulsar={() => WebBrowser.openBrowserAsync(elemento.url)}
      />
      <Boton etiqueta="Editar etiquetas" variante="secundario" alPulsar={() => setEditando(true)} />
      <Boton etiqueta="Eliminar" variante="peligro" alPulsar={confirmarEliminar} />

      {editando ? (
        <DialogoTexto
          titulo="Editar etiquetas"
          etiquetaCampo="Etiquetas, separadas por comas"
          valorInicial={elemento.etiquetas.join(', ')}
          alAceptar={(valor) => {
            const etiquetas = valor
              .split(',')
              .map((e) => e.trim())
              .filter(Boolean);
            editarEtiquetas(elemento.id, etiquetas);
            setEditando(false);
            anunciarImportante('Etiquetas actualizadas');
          }}
          alCancelar={() => setEditando(false)}
        />
      ) : null}
    </ScrollView>
  );
}
