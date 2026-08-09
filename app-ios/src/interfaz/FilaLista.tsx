import { Pressable, StyleSheet, Text, View } from 'react-native';

import { ALTURA_MINIMA_TOQUE, ESPACIADO, useTema } from './tema';

export type AccionFila = {
  /** Identificador interno de la acción. */
  nombre: string;
  /** Texto que lee el rotor de VoiceOver y que aparece en el botón. */
  etiqueta: string;
  ejecutar: () => void;
  destructiva?: boolean;
};

type Props = {
  titulo: string;
  subtitulo?: string;
  /** Se ejecuta al tocar la fila y con el doble toque de VoiceOver. */
  accionPrincipal: AccionFila;
  accionesSecundarias?: AccionFila[];
  /** Permite devolver aquí el foco de VoiceOver al cerrar un diálogo. */
  ref?: React.Ref<View>;
};

/**
 * Fila de lista con acciones.
 *
 * La fila entera es un solo elemento accesible: VoiceOver lee "título,
 * subtítulo" y ofrece las acciones secundarias en el rotor. Los botones
 * visibles quedan ocultos al lector precisamente porque el contenedor agrupa
 * (`accessible`), que es lo que evita tener que recorrer 3 elementos por fila.
 */
export function FilaLista({
  titulo,
  subtitulo,
  accionPrincipal,
  accionesSecundarias = [],
  ref,
}: Props) {
  const tema = useTema();

  return (
    <Pressable
      ref={ref}
      accessible
      accessibilityRole="button"
      accessibilityLabel={subtitulo ? `${titulo}. ${subtitulo}` : titulo}
      accessibilityActions={accionesSecundarias.map((accion) => ({
        name: accion.nombre,
        label: accion.etiqueta,
      }))}
      onAccessibilityAction={(evento) => {
        const accion = accionesSecundarias.find((a) => a.nombre === evento.nativeEvent.actionName);
        accion?.ejecutar();
      }}
      onPress={accionPrincipal.ejecutar}
      // Con botones visibles dentro de un contenedor agrupado, el doble
      // toque de VoiceOver se traduce en un toque sintético en el centro
      // del elemento; sin esto, si un botón hijo cae ahí, se activa ese en
      // vez de la acción principal (GUIA-ACCESIBILIDAD-RN.md §4).
      onAccessibilityTap={accionPrincipal.ejecutar}
      style={({ pressed }) => [
        estilos.fila,
        { backgroundColor: tema.superficie, borderColor: tema.borde, opacity: pressed ? 0.75 : 1 },
      ]}>
      <View style={estilos.textos}>
        <Text style={[estilos.titulo, { color: tema.texto }]}>{titulo}</Text>
        {subtitulo ? (
          <Text style={[estilos.subtitulo, { color: tema.textoSecundario }]}>{subtitulo}</Text>
        ) : null}
      </View>

      {accionesSecundarias.length > 0 ? (
        <View style={estilos.acciones}>
          {accionesSecundarias.map((accion) => (
            <Pressable key={accion.nombre} onPress={accion.ejecutar} style={estilos.botonAccion}>
              <Text
                style={[
                  estilos.textoAccion,
                  { color: accion.destructiva ? tema.peligro : tema.acento },
                ]}>
                {accion.etiqueta}
              </Text>
            </Pressable>
          ))}
        </View>
      ) : null}
    </Pressable>
  );
}

const estilos = StyleSheet.create({
  fila: {
    minHeight: ALTURA_MINIMA_TOQUE,
    borderRadius: 12,
    borderWidth: 1,
    padding: ESPACIADO.medio,
    gap: ESPACIADO.pequeno,
  },
  textos: { gap: 2 },
  titulo: { fontSize: 17, fontWeight: '600' },
  subtitulo: { fontSize: 15 },
  acciones: { flexDirection: 'row', flexWrap: 'wrap', gap: ESPACIADO.medio },
  botonAccion: { minHeight: 40, justifyContent: 'center' },
  textoAccion: { fontSize: 16, fontWeight: '600' },
});
