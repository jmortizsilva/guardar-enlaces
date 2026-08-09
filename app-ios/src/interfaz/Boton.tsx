import { ActivityIndicator, Pressable, StyleSheet, Text } from 'react-native';

import { ALTURA_MINIMA_TOQUE, ESPACIADO, useTema } from './tema';

type Variante = 'principal' | 'secundario' | 'peligro';

type Props = {
  etiqueta: string;
  alPulsar: () => void;
  variante?: Variante;
  deshabilitado?: boolean;
  /** En curso y sin admitir más toques: muestra indicador y se atenúa. */
  ocupado?: boolean;
  /**
   * Muestra el indicador de actividad pero deja el botón utilizable.
   * Para acciones que se pueden interrumpir, como detener una búsqueda.
   */
  indicador?: boolean;
  /** Contexto extra que VoiceOver lee tras la etiqueta. */
  pista?: string;
};

export function Boton({
  etiqueta,
  alPulsar,
  variante = 'principal',
  deshabilitado = false,
  ocupado = false,
  indicador = false,
  pista,
}: Props) {
  const tema = useTema();
  const inactivo = deshabilitado || ocupado;

  const colorFondo =
    variante === 'principal' ? tema.acento : variante === 'peligro' ? tema.peligro : 'transparent';
  const colorTexto = variante === 'secundario' ? tema.acento : tema.textoSobreAcento;

  return (
    <Pressable
      onPress={alPulsar}
      disabled={inactivo}
      accessibilityRole="button"
      accessibilityLabel={etiqueta}
      accessibilityHint={pista}
      // Solo `disabled`. Añadir `busy` haría que VoiceOver dijera "atenuado" y
      // "ocupado" para la misma situación: información repetida y ruidosa.
      accessibilityState={{ disabled: inactivo }}
      style={({ pressed }) => [
        estilos.base,
        {
          backgroundColor: colorFondo,
          borderColor: variante === 'secundario' ? tema.acento : colorFondo,
          opacity: inactivo ? 0.5 : pressed ? 0.75 : 1,
        },
      ]}>
      {ocupado || indicador ? <ActivityIndicator color={colorTexto} /> : null}
      <Text style={[estilos.texto, { color: colorTexto }]}>{etiqueta}</Text>
    </Pressable>
  );
}

const estilos = StyleSheet.create({
  base: {
    minHeight: ALTURA_MINIMA_TOQUE,
    borderRadius: 12,
    borderWidth: 2,
    paddingHorizontal: ESPACIADO.medio,
    paddingVertical: ESPACIADO.pequeno,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: ESPACIADO.pequeno,
  },
  texto: {
    fontSize: 17,
    fontWeight: '600',
    textAlign: 'center',
  },
});
