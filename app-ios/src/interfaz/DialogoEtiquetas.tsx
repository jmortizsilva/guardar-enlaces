import { useState } from 'react';
import { Modal, Text, View, StyleSheet } from 'react-native';

import { Boton } from './Boton';
import { SelectorEtiquetas } from './SelectorEtiquetas';
import { ESPACIADO, useTema } from './tema';

type Props = {
  disponibles: readonly string[];
  seleccionadas: readonly string[];
  alAceptar: (etiquetas: string[]) => void;
  alCancelar: () => void;
};

/**
 * Mismo envoltorio de modal accesible que DialogoTexto (accessibilityViewIsModal,
 * título como header), pero alojando el selector de etiquetas por chips en
 * vez de un campo de texto libre.
 */
export function DialogoEtiquetas({ disponibles, seleccionadas, alAceptar, alCancelar }: Props) {
  const tema = useTema();
  const [valor, setValor] = useState<string[]>([...seleccionadas]);

  return (
    <Modal
      visible
      transparent
      animationType="fade"
      onRequestClose={alCancelar}
      accessibilityViewIsModal
      supportedOrientations={['portrait']}>
      <View style={estilos.fondo}>
        <View
          style={[estilos.caja, { backgroundColor: tema.fondo, borderColor: tema.borde }]}
          accessibilityViewIsModal>
          <Text accessibilityRole="header" style={[estilos.titulo, { color: tema.texto }]}>
            Editar etiquetas
          </Text>

          <SelectorEtiquetas disponibles={disponibles} seleccionadas={valor} alCambiar={setValor} />

          <View style={estilos.botones}>
            <Boton etiqueta="Cancelar" variante="secundario" alPulsar={alCancelar} />
            <Boton etiqueta="Guardar" alPulsar={() => alAceptar(valor)} />
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
  botones: { gap: ESPACIADO.pequeno },
});
