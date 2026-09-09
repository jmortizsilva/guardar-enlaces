import { router } from 'expo-router';
import * as Updates from 'expo-updates';
import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';

import { establecerModoSilencioso, obtenerModoSilencioso } from '../../modules/guardado-silencioso';
import { comprobarActualizacion } from '../../src/actualizaciones/actualizaciones';
import { useSesion } from '../../src/contexto/ProveedorApp';
import { Boton } from '../../src/interfaz/Boton';
import { Interruptor } from '../../src/interfaz/Interruptor';
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
  const [modoSilencioso, setModoSilencioso] = useState(false);

  useEffect(() => {
    obtenerModoSilencioso().then(setModoSilencioso);
  }, []);

  function alCambiarModoSilencioso(valor: boolean): void {
    setModoSilencioso(valor);
    establecerModoSilencioso(valor);
  }

  return (
    <View
      style={{
        flex: 1,
        padding: ESPACIADO.grande,
        gap: ESPACIADO.medio,
        backgroundColor: tema.fondo,
      }}>
      <Boton etiqueta="Volver" variante="secundario" alPulsar={() => router.back()} />

      {sesion.autenticado ? (
        <>
          <Text style={{ color: tema.texto, fontSize: 17 }}>
            Sesión iniciada como {sesion.usuario?.email}. Tus enlaces se sincronizan con el PC.
          </Text>
          <Boton
            etiqueta="Cerrar sesión"
            variante="peligro"
            alPulsar={() => sesion.cerrar()}
            pista="Los enlaces se quedan en este iPhone y la aplicación sigue funcionando sin cuenta"
          />
        </>
      ) : (
        <>
          <Text style={{ color: tema.texto, fontSize: 17 }}>
            Sin cuenta: los enlaces se guardan solo en este iPhone.
          </Text>
          <Boton
            etiqueta="Entrar con Google"
            alPulsar={() => router.push('/login')}
            pista="Hace falta para tener los mismos enlaces en el iPhone y en el PC"
          />
        </>
      )}

      <Interruptor
        etiqueta="Guardar sin abrir la app al compartir"
        valor={modoSilencioso}
        alCambiar={alCambiarModoSilencioso}
        deshabilitado={!sesion.autenticado}
        pista={
          sesion.autenticado
            ? 'Al compartir un enlace desde Safari, se guarda directamente y te quedas donde estabas. Sin vista previa ni etiquetas en el momento; se completan después desde la app.'
            : 'Necesita cuenta: al compartir en silencio, el enlace lo guarda el servidor. Sin cuenta, compartir abre la aplicación para guardarlo aquí.'
        }
      />
      <Boton
        etiqueta="Buscar actualizaciones"
        variante="secundario"
        alPulsar={() => comprobarActualizacion({ manual: true })}
      />
      <Text style={{ color: tema.textoSecundario, fontSize: 13 }}>
        {descripcionActualizacion()}
      </Text>
    </View>
  );
}
