import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { Boton } from './Boton';
import { ALTURA_MINIMA_TOQUE, ESPACIADO, useTema } from './tema';

type Props = {
  /** Etiquetas ya usadas en otros elementos (dominio/sincronizacion.etiquetasDisponibles). */
  disponibles: readonly string[];
  seleccionadas: readonly string[];
  alCambiar: (etiquetas: string[]) => void;
};

/**
 * Selector de etiquetas por chips, de selección múltiple: a diferencia de
 * SelectorDesplegable (selección única, para filtrar la lista), aquí un
 * elemento puede llevar varias etiquetas a la vez. Incluye un campo para dar
 * de alta una etiqueta que todavía no existe en ningún otro elemento.
 */
export function SelectorEtiquetas({ disponibles, seleccionadas, alCambiar }: Props) {
  const tema = useTema();
  const [nueva, setNueva] = useState('');

  // La seleccionada puede no estar en "disponibles" si es de alta reciente.
  const opciones = [...new Set([...disponibles, ...seleccionadas])];

  function alternar(etiqueta: string): void {
    const activa = seleccionadas.includes(etiqueta);
    alCambiar(activa ? seleccionadas.filter((e) => e !== etiqueta) : [...seleccionadas, etiqueta]);
  }

  function anadirNueva(): void {
    const limpia = nueva.trim();
    if (!limpia || seleccionadas.includes(limpia)) return;
    alCambiar([...seleccionadas, limpia]);
    setNueva('');
  }

  return (
    <View style={estilos.contenedor}>
      {opciones.length > 0 ? (
        <View style={estilos.fila}>
          {opciones.map((etiqueta) => {
            const activa = seleccionadas.includes(etiqueta);
            return (
              <Pressable
                key={etiqueta}
                onPress={() => alternar(etiqueta)}
                accessibilityRole="button"
                accessibilityLabel={etiqueta}
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
                  {etiqueta}
                </Text>
              </Pressable>
            );
          })}
        </View>
      ) : null}

      <View style={estilos.filaNueva}>
        <TextInput
          value={nueva}
          onChangeText={setNueva}
          placeholder="Nueva etiqueta"
          placeholderTextColor={tema.textoSecundario}
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="done"
          onSubmitEditing={anadirNueva}
          accessibilityLabel="Nueva etiqueta"
          style={[estilos.campo, { borderColor: tema.borde, color: tema.texto }]}
        />
        <Boton
          etiqueta="Añadir"
          variante="secundario"
          alPulsar={anadirNueva}
          deshabilitado={!nueva.trim()}
        />
      </View>
    </View>
  );
}

const estilos = StyleSheet.create({
  contenedor: { gap: ESPACIADO.medio },
  fila: { flexDirection: 'row', flexWrap: 'wrap', gap: ESPACIADO.pequeno },
  chip: {
    minHeight: ALTURA_MINIMA_TOQUE,
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: ESPACIADO.medio,
    justifyContent: 'center',
  },
  filaNueva: { flexDirection: 'row', gap: ESPACIADO.pequeno, alignItems: 'center' },
  campo: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 8,
    padding: ESPACIADO.medio,
    fontSize: 17,
    minHeight: ALTURA_MINIMA_TOQUE,
  },
});
