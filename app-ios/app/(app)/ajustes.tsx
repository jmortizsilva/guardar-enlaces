import { router } from 'expo-router';
import { Text, View } from 'react-native';

import { useSesion } from '../../src/contexto/ProveedorApp';
import { Boton } from '../../src/interfaz/Boton';
import { ESPACIADO, useTema } from '../../src/interfaz/tema';

export default function Ajustes() {
  const tema = useTema();
  const sesion = useSesion();

  return (
    <View
      style={{
        flex: 1,
        padding: ESPACIADO.grande,
        gap: ESPACIADO.medio,
        backgroundColor: tema.fondo,
      }}>
      <Boton etiqueta="Volver" variante="secundario" alPulsar={() => router.back()} />
      <Text style={{ color: tema.texto, fontSize: 17 }}>
        Sesión iniciada como {sesion.usuario?.email}
      </Text>
      <Boton etiqueta="Cerrar sesión" variante="peligro" alPulsar={() => sesion.cerrar()} />
    </View>
  );
}
