import { router } from 'expo-router';
import * as Updates from 'expo-updates';
import { Text, View } from 'react-native';

import { useSesion } from '../../src/contexto/ProveedorApp';
import { Boton } from '../../src/interfaz/Boton';
import { ESPACIADO, useTema } from '../../src/interfaz/tema';

/** Para confirmar si una actualizacion OTA ha llegado de verdad al abrir la app. */
function descripcionActualizacion(): string {
  if (Updates.isEmbeddedLaunch) return 'Sin actualizaciones OTA aplicadas (versión de fábrica)';
  const fecha = Updates.createdAt?.toLocaleString('es-ES') ?? 'fecha desconocida';
  const id = Updates.updateId?.slice(0, 8) ?? '?';
  return `Actualización ${id} · ${fecha}`;
}

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
      <Text style={{ color: tema.textoSecundario, fontSize: 13 }}>
        {descripcionActualizacion()}
      </Text>
    </View>
  );
}
