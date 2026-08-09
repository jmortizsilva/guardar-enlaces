import { useState } from 'react';
import { Modal, StyleSheet, Text, TextInput, View } from 'react-native';

import { Boton } from './Boton';
import { ESPACIADO, useTema } from './tema';

type Props = {
  titulo: string;
  /** Etiqueta del campo. Se mantiene siempre, escriba lo que escriba el usuario. */
  etiquetaCampo: string;
  valorInicial?: string;
  textoAceptar?: string;
  alAceptar: (valor: string) => void;
  alCancelar: () => void;
};

/**
 * Diálogo propio para pedir un texto.
 *
 * Sustituye a Alert.prompt por dos motivos de accesibilidad que el diálogo
 * nativo no permite arreglar:
 *  - Su título no se anuncia como encabezado.
 *  - Al escribir, VoiceOver deja de leer para qué era el campo, porque el
 *    texto introducido pasa a ser su nombre accesible.
 *
 * Aquí el campo lleva su propia accessibilityLabel, que no cambia nunca, y el
 * foco entra en el campo al abrir.
 *
 * Se monta solo cuando hace falta: así el valor inicial se toma al crearlo y no
 * hay que sincronizarlo después con un efecto.
 */
export function DialogoTexto({
  titulo,
  etiquetaCampo,
  valorInicial = '',
  textoAceptar = 'Guardar',
  alAceptar,
  alCancelar,
}: Props) {
  const tema = useTema();
  const [valor, setValor] = useState(valorInicial);

  const aceptar = () => {
    const limpio = valor.trim();
    if (limpio) alAceptar(limpio);
  };

  return (
    <Modal
      visible
      transparent
      animationType="fade"
      onRequestClose={alCancelar}
      // Impide que VoiceOver se escape a lo que hay detrás del diálogo.
      accessibilityViewIsModal
      supportedOrientations={['portrait']}>
      <View style={estilos.fondo}>
        <View
          style={[estilos.caja, { backgroundColor: tema.fondo, borderColor: tema.borde }]}
          accessibilityViewIsModal>
          <Text accessibilityRole="header" style={[estilos.titulo, { color: tema.texto }]}>
            {titulo}
          </Text>

          {/* Se oculta al lector: el campo ya lleva este mismo texto como
              nombre accesible, y sin esto VoiceOver lo lee dos veces, una en la
              etiqueta suelta y otra en el campo. Es el equivalente a aria-hidden. */}
          <Text
            accessibilityElementsHidden
            importantForAccessibility="no-hide-descendants"
            style={[estilos.etiqueta, { color: tema.textoSecundario }]}>
            {etiquetaCampo}
          </Text>

          <TextInput
            value={valor}
            onChangeText={setValor}
            // La etiqueta va aquí y no cambia: sin esto, al escribir VoiceOver
            // lee solo el contenido y se pierde para qué servía el campo.
            accessibilityLabel={etiquetaCampo}
            autoFocus
            returnKeyType="done"
            onSubmitEditing={aceptar}
            style={[estilos.campo, { color: tema.texto, borderColor: tema.borde }]}
          />

          <View style={estilos.botones}>
            <Boton etiqueta="Cancelar" variante="secundario" alPulsar={alCancelar} />
            <Boton etiqueta={textoAceptar} alPulsar={aceptar} deshabilitado={!valor.trim()} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

const estilos = StyleSheet.create({
  fondo: {
    flex: 1,
    justifyContent: 'center',
    padding: ESPACIADO.grande,
    backgroundColor: 'rgba(0,0,0,0.5)',
  },
  caja: { borderRadius: 16, borderWidth: 1, padding: ESPACIADO.grande, gap: ESPACIADO.medio },
  titulo: { fontSize: 22, fontWeight: '700' },
  etiqueta: { fontSize: 15 },
  campo: {
    borderWidth: 1,
    borderRadius: 12,
    padding: ESPACIADO.medio,
    fontSize: 17,
    minHeight: 48,
  },
  botones: { gap: ESPACIADO.pequeno },
});
