import { useRef, useState } from 'react';
import { Keyboard, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { ALTURA_MINIMA_TOQUE, ESPACIADO, useTema } from './tema';

type Props = {
  /** Se llama con el texto en cada cambio, y con '' al pulsar "Cancelar". */
  alCambiarTexto: (texto: string) => void;
  marcador: string;
  /** Nombre accesible del campo (accessibilityLabel). */
  etiqueta: string;
  /** Contexto extra que VoiceOver lee tras la etiqueta (accessibilityHint). */
  pista?: string;
  autoFoco?: boolean;
};

/**
 * Campo de búsqueda con un botón "Cancelar" accesible que borra el texto y cierra
 * el teclado.
 *
 * Por qué hace falta el "Cancelar": con el teclado abierto no hay forma fiable de
 * cerrarlo con VoiceOver —el gesto de "atrás" (scrub con dos dedos) NO lo oculta— y,
 * además, el teclado tapa la barra de pestañas, así que sin este botón el usuario
 * queda atrapado en la pantalla. El botón va en la misma fila y DESPUÉS del campo,
 * para que el orden de lectura de VoiceOver sea campo → cancelar.
 *
 * El campo va NO controlado (sin `value`): con la arquitectura nueva de RN, un
 * TextInput controlado duplica el texto al dictar en iOS (el reconciliador reaplica
 * el valor mientras el dictado sigue insertando). Se rastrea por `onChangeText` y se
 * limpia por `ref`.
 */
export function CampoBusqueda({ alCambiarTexto, marcador, etiqueta, pista, autoFoco }: Props) {
  const tema = useTema();
  const refCampo = useRef<TextInput>(null);
  const [enfocado, setEnfocado] = useState(false);
  const [tieneTexto, setTieneTexto] = useState(false);

  // "Cancelar" solo tiene sentido si hay algo que cancelar: foco (teclado abierto)
  // o texto escrito. Tras cancelar (sin foco y vacío) desaparece.
  const mostrarCancelar = enfocado || tieneTexto;

  const cambiar = (texto: string) => {
    setTieneTexto(texto.length > 0);
    alCambiarTexto(texto);
  };

  const cancelar = () => {
    refCampo.current?.clear();
    setTieneTexto(false);
    alCambiarTexto('');
    Keyboard.dismiss();
  };

  return (
    <View style={estilos.fila}>
      <TextInput
        ref={refCampo}
        onChangeText={cambiar}
        placeholder={marcador}
        placeholderTextColor={tema.textoSecundario}
        style={[
          estilos.campo,
          { backgroundColor: tema.superficie, color: tema.texto, borderColor: tema.borde },
        ]}
        // Tecla "Buscar" en el teclado; al pulsarla se cierra y quedan los resultados.
        returnKeyType="search"
        onSubmitEditing={() => Keyboard.dismiss()}
        onFocus={() => setEnfocado(true)}
        onBlur={() => setEnfocado(false)}
        autoCorrect={false}
        autoFocus={autoFoco}
        accessibilityLabel={etiqueta}
        accessibilityHint={pista}
      />
      {mostrarCancelar && (
        <Pressable
          style={estilos.cancelar}
          onPress={cancelar}
          accessibilityRole="button"
          accessibilityLabel="Cancelar búsqueda"
          accessibilityHint="Borra el texto y cierra el teclado">
          <Text style={[estilos.cancelarTexto, { color: tema.acento }]}>Cancelar</Text>
        </Pressable>
      )}
    </View>
  );
}

const estilos = StyleSheet.create({
  fila: { flexDirection: 'row', alignItems: 'center', gap: ESPACIADO.pequeno },
  campo: {
    flex: 1,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: ESPACIADO.medio,
    minHeight: ALTURA_MINIMA_TOQUE,
    fontSize: 17,
  },
  cancelar: {
    minHeight: ALTURA_MINIMA_TOQUE,
    justifyContent: 'center',
    paddingHorizontal: ESPACIADO.pequeno,
  },
  cancelarTexto: { fontSize: 17, fontWeight: '600' },
});
