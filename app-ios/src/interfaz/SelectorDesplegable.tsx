import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Boton } from './Boton';
import { ALTURA_MINIMA_TOQUE, ESPACIADO, useTema } from './tema';

type Props = {
  /** Etiquetas distintas presentes (dominio/sincronizacion.etiquetasDisponibles). */
  etiquetas: readonly string[];
  /** null = "Todas" (sin filtrar). */
  seleccionada: string | null;
  alSeleccionar: (etiqueta: string | null) => void;
};

const TODAS = 'Todas';

/**
 * Desplegable de selección única para filtrar por etiqueta: un botón que
 * muestra el filtro activo y abre una lista al tocarlo, en vez de una fila
 * de chips (que dejaba de caber con muchas etiquetas).
 */
export function SelectorDesplegable({ etiquetas, seleccionada, alSeleccionar }: Props) {
  const tema = useTema();
  const [abierto, setAbierto] = useState(false);
  const opciones: (string | null)[] = [null, ...etiquetas];

  function elegir(etiqueta: string | null): void {
    alSeleccionar(etiqueta);
    setAbierto(false);
  }

  return (
    <>
      <Pressable
        onPress={() => setAbierto(true)}
        accessibilityRole="button"
        accessibilityLabel={`Filtrar por etiqueta: ${seleccionada ?? TODAS}`}
        accessibilityHint="Toca para cambiar el filtro"
        style={[estilos.campo, { borderColor: tema.borde, backgroundColor: tema.superficie }]}>
        <Text style={[estilos.textoCampo, { color: tema.texto }]}>{seleccionada ?? TODAS}</Text>
        <Text style={[estilos.flecha, { color: tema.textoSecundario }]}>▾</Text>
      </Pressable>

      <Modal
        visible={abierto}
        transparent
        animationType="fade"
        onRequestClose={() => setAbierto(false)}
        accessibilityViewIsModal
        supportedOrientations={['portrait']}>
        <View style={estilos.fondo}>
          <View
            style={[estilos.caja, { backgroundColor: tema.fondo, borderColor: tema.borde }]}
            accessibilityViewIsModal>
            <Text accessibilityRole="header" style={[estilos.titulo, { color: tema.texto }]}>
              Filtrar por etiqueta
            </Text>
            <ScrollView style={estilos.lista}>
              {opciones.map((etiqueta) => {
                const activa = etiqueta === seleccionada;
                return (
                  <Pressable
                    key={etiqueta ?? '__todas__'}
                    onPress={() => elegir(etiqueta)}
                    accessibilityRole="button"
                    accessibilityLabel={etiqueta ?? TODAS}
                    accessibilityState={{ selected: activa }}
                    style={[
                      estilos.opcion,
                      { backgroundColor: activa ? tema.acento : 'transparent' },
                    ]}>
                    <Text
                      style={{
                        color: activa ? tema.textoSobreAcento : tema.texto,
                        fontSize: 17,
                        fontWeight: activa ? '700' : '500',
                      }}>
                      {etiqueta ?? TODAS}
                    </Text>
                  </Pressable>
                );
              })}
            </ScrollView>

            <Boton etiqueta="Cerrar" variante="secundario" alPulsar={() => setAbierto(false)} />
          </View>
        </View>
      </Modal>
    </>
  );
}

const estilos = StyleSheet.create({
  campo: {
    minHeight: ALTURA_MINIMA_TOQUE,
    borderRadius: 8,
    borderWidth: 1,
    paddingHorizontal: ESPACIADO.medio,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  textoCampo: { fontSize: 17, fontWeight: '500' },
  flecha: { fontSize: 15 },
  fondo: {
    flex: 1,
    justifyContent: 'center',
    padding: ESPACIADO.grande,
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  caja: {
    borderRadius: 16,
    borderWidth: 1,
    padding: ESPACIADO.grande,
    gap: ESPACIADO.medio,
    maxHeight: '70%',
  },
  titulo: { fontSize: 22, fontWeight: '700' },
  lista: { flexGrow: 0 },
  opcion: {
    minHeight: ALTURA_MINIMA_TOQUE,
    borderRadius: 8,
    paddingHorizontal: ESPACIADO.medio,
    justifyContent: 'center',
  },
});
