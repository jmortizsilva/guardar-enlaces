import { Pressable, ScrollView, StyleSheet, Text } from 'react-native';

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
 * Selector de etiqueta como fila de chips, en vez de un picker nativo de
 * terceros: evita depender de un paquete no verificado en Expo Go y que
 * pudiera forzar una development build (ver plan de app-ios).
 */
export function ChipEtiqueta({ etiquetas, seleccionada, alSeleccionar }: Props) {
  const tema = useTema();
  const opciones: (string | null)[] = [null, ...etiquetas];

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={estilos.fila}
      accessibilityRole="tablist">
      {opciones.map((etiqueta) => {
        const activa = etiqueta === seleccionada;
        return (
          <Pressable
            key={etiqueta ?? '__todas__'}
            onPress={() => alSeleccionar(etiqueta)}
            accessibilityRole="button"
            accessibilityLabel={etiqueta ?? TODAS}
            accessibilityState={{ selected: activa }}
            style={[
              estilos.chip,
              {
                borderColor: activa ? tema.acento : tema.borde,
                backgroundColor: activa ? tema.acento : tema.superficie,
              },
            ]}>
            <Text
              style={{
                color: activa ? tema.textoSobreAcento : tema.texto,
                fontSize: 15,
                fontWeight: activa ? '700' : '500',
              }}>
              {etiqueta ?? TODAS}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const estilos = StyleSheet.create({
  fila: { flexDirection: 'row', gap: ESPACIADO.pequeno },
  chip: {
    minHeight: ALTURA_MINIMA_TOQUE,
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: ESPACIADO.medio,
    justifyContent: 'center',
  },
});
