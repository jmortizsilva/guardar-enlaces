import { Pressable, StyleSheet, Switch, Text } from 'react-native';

import { ALTURA_MINIMA_TOQUE, ESPACIADO, useTema } from './tema';

type Props = {
  etiqueta: string;
  valor: boolean;
  alCambiar: (valor: boolean) => void;
  pista?: string;
  deshabilitado?: boolean;
};

/**
 * Interruptor con su etiqueta como un único elemento accesible.
 *
 * Poner un <Switch> junto a un <Text> suelto hace que VoiceOver los lea en dos
 * pasadas distintas: primero el texto, luego un interruptor sin contexto. Al
 * agrupar con `accessible` y rol "switch" se lee de una vez y el doble toque
 * lo activa. El Switch interior sigue funcionando al tacto.
 */
export function Interruptor({ etiqueta, valor, alCambiar, pista, deshabilitado = false }: Props) {
  const tema = useTema();

  return (
    <Pressable
      accessible
      accessibilityRole="switch"
      accessibilityLabel={etiqueta}
      accessibilityHint={pista}
      accessibilityState={{ checked: valor, disabled: deshabilitado }}
      onPress={() => alCambiar(!valor)}
      disabled={deshabilitado}
      style={[estilos.fila, { opacity: deshabilitado ? 0.5 : 1 }]}>
      <Text style={[estilos.etiqueta, { color: tema.texto }]}>{etiqueta}</Text>
      <Switch value={valor} onValueChange={alCambiar} disabled={deshabilitado} />
    </Pressable>
  );
}

const estilos = StyleSheet.create({
  fila: {
    minHeight: ALTURA_MINIMA_TOQUE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: ESPACIADO.medio,
  },
  etiqueta: { fontSize: 17, flexShrink: 1 },
});
