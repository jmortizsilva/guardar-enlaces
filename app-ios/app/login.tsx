import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { anunciarImportante } from '../src/accesibilidad/anuncios';
import { useSesion } from '../src/contexto/ProveedorApp';
import { Boton } from '../src/interfaz/Boton';
import { ESPACIADO, useTema } from '../src/interfaz/tema';

/**
 * Login con Google (ver backend/docs/CONTRATO-API.md). Solo entra quien tenga
 * el correo en la lista de invitados del servidor; a quien no, el backend le
 * devuelve "sin_invitacion" y aqui se cuenta con esas palabras.
 *
 * El consentimiento se abre en una hoja del sistema (ASWebAuthenticationSession),
 * fuera de la app: iOS pregunta antes si se permite usar google.com para iniciar
 * sesion. Por eso el boton avisa de que se va a abrir Safari, para que el aviso
 * del sistema no llegue de sorpresa.
 */
export default function Login() {
  const tema = useTema();
  const sesion = useSesion();
  const [entrando, setEntrando] = useState(false);
  const [error, setError] = useState('');

  const alEntrar = async () => {
    setEntrando(true);
    setError('');
    try {
      const resultado = await sesion.iniciarConGoogle();
      if (resultado.estado === 'exito') {
        router.replace('/');
        return;
      }
      if (resultado.estado === 'error') {
        setError(resultado.mensaje);
        anunciarImportante(resultado.mensaje);
      }
      // 'cancelado' no se anuncia: lo ha hecho el usuario y, al cerrarse la
      // hoja, VoiceOver ya vuelve a leer esta pantalla.
    } catch (fallo) {
      const mensaje = fallo instanceof Error ? fallo.message : 'No se pudo iniciar sesión.';
      setError(mensaje);
      anunciarImportante(mensaje);
    } finally {
      setEntrando(false);
    }
  };

  return (
    <View style={[estilos.contenedor, { backgroundColor: tema.fondo }]}>
      <Text accessibilityRole="header" style={[estilos.titulo, { color: tema.texto }]}>
        Guardar enlaces
      </Text>
      <Text style={[estilos.aviso, { color: tema.textoSecundario }]}>
        Para usar la aplicación necesitas entrar con tu cuenta de Google.
      </Text>

      {error ? (
        <Text accessibilityLiveRegion="polite" style={[estilos.error, { color: tema.peligro }]}>
          {error}
        </Text>
      ) : null}

      <Boton
        etiqueta="Entrar con Google"
        alPulsar={alEntrar}
        ocupado={entrando}
        pista="Se abre Safari para confirmar tu cuenta y vuelves aquí al terminar"
      />
    </View>
  );
}

const estilos = StyleSheet.create({
  contenedor: {
    flex: 1,
    justifyContent: 'center',
    padding: ESPACIADO.grande,
    gap: ESPACIADO.medio,
  },
  titulo: { fontSize: 28, fontWeight: '700' },
  aviso: { fontSize: 17 },
  error: { fontSize: 15 },
});
