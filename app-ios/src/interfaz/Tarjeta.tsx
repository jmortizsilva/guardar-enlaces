import { StyleSheet, Text, View } from 'react-native';

import { ESPACIADO, useTema } from './tema';

export type FilaTarjeta = { etiqueta: string; visible: string };

type Props = {
  /** Lo que VoiceOver lee de una vez para todo el bloque. */
  etiquetaAccesible: string;
  /** Título pequeño sobre el valor destacado. */
  etiqueta?: string;
  /** Valor grande, para el dato principal. */
  destacado?: string;
  colorDestacado?: string;
  filas?: FilaTarjeta[];
};

/**
 * Bloque de datos que VoiceOver recorre en un solo gesto.
 *
 * Agrupar con `accessible` es lo que evita tener que pasar por cada métrica:
 * el precio es que las filas interiores dejan de ser accesibles por separado,
 * y por eso la etiqueta accesible se compone fuera, con las unidades leídas.
 */
export function Tarjeta({
  etiquetaAccesible,
  etiqueta,
  destacado,
  colorDestacado,
  filas = [],
}: Props) {
  const tema = useTema();

  return (
    <View
      accessible
      accessibilityLabel={etiquetaAccesible}
      style={[estilos.tarjeta, { backgroundColor: tema.superficie, borderColor: tema.borde }]}>
      {etiqueta ? (
        <Text style={[estilos.etiqueta, { color: tema.textoSecundario }]}>{etiqueta}</Text>
      ) : null}

      {destacado ? (
        <Text style={[estilos.destacado, { color: colorDestacado ?? tema.texto }]}>
          {destacado}
        </Text>
      ) : null}

      {filas.map((fila) => (
        <View key={fila.etiqueta} style={estilos.fila}>
          <Text style={[estilos.etiqueta, { color: tema.textoSecundario }]}>{fila.etiqueta}</Text>
          <Text style={[estilos.valor, { color: tema.texto }]}>{fila.visible}</Text>
        </View>
      ))}
    </View>
  );
}

const estilos = StyleSheet.create({
  tarjeta: { borderRadius: 12, borderWidth: 1, padding: ESPACIADO.medio, gap: ESPACIADO.pequeno },
  destacado: { fontSize: 28, fontWeight: '700' },
  fila: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    gap: ESPACIADO.medio,
  },
  etiqueta: { fontSize: 15 },
  valor: { fontSize: 22, fontWeight: '700' },
});
