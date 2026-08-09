import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';

import { anunciarImportante } from '../src/accesibilidad/anuncios';
import { useSesion } from '../src/contexto/ProveedorApp';
import { Boton } from '../src/interfaz/Boton';
import { ESPACIADO, useTema } from '../src/interfaz/tema';

/**
 * Login DE DESARROLLO: pide un correo ya invitado y llama a
 * POST /auth/dev-login (solo funciona si el servidor tiene
 * PERMITIR_LOGIN_DEV=true). Sustituye temporalmente al flujo real con
 * Google/Apple (ver backend/docs/CONTRATO-API.md): useSesion().iniciarConDevLogin
 * vive detras de la misma interfaz que usara el login real, asi que
 * cambiarlo despues no toca el resto de la app.
 */
export default function Login() {
  const tema = useTema();
  const sesion = useSesion();
  const [correo, setCorreo] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');

  const alEntrar = async () => {
    if (!correo.includes('@')) {
      setError('Escribe un correo válido.');
      anunciarImportante('Escribe un correo válido.');
      return;
    }

    setEnviando(true);
    setError('');
    try {
      await sesion.iniciarConDevLogin(correo.trim());
      router.replace('/');
    } catch (fallo) {
      const mensaje = fallo instanceof Error ? fallo.message : 'No se pudo iniciar sesión.';
      setError(mensaje);
      anunciarImportante(mensaje);
    } finally {
      setEnviando(false);
    }
  };

  return (
    <View style={[estilos.contenedor, { backgroundColor: tema.fondo }]}>
      <Text style={[estilos.aviso, { color: tema.textoSecundario }]}>
        Modo de desarrollo: escribe un correo ya invitado. No pasa por Google ni Apple todavía.
      </Text>

      <Text style={[estilos.etiquetaCampo, { color: tema.texto }]}>Correo electrónico</Text>
      <TextInput
        value={correo}
        onChangeText={setCorreo}
        placeholder="persona@ejemplo.com"
        placeholderTextColor={tema.textoSecundario}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="email-address"
        textContentType="emailAddress"
        // El placeholder no basta como nombre accesible (mismo hallazgo que
        // wx.TextCtrl.SetName() en app-windows): sin esto VoiceOver anuncia
        // "campo de edicion" a secas.
        accessibilityLabel="Correo electrónico"
        style={[estilos.campo, { borderColor: tema.borde, color: tema.texto }]}
      />

      {error ? (
        <Text accessibilityLiveRegion="polite" style={[estilos.error, { color: tema.peligro }]}>
          {error}
        </Text>
      ) : null}

      <Boton etiqueta="Entrar" alPulsar={alEntrar} ocupado={enviando} deshabilitado={!correo} />
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
  aviso: { fontSize: 15 },
  etiquetaCampo: { fontSize: 15, fontWeight: '600' },
  campo: {
    borderWidth: 1,
    borderRadius: 8,
    padding: ESPACIADO.medio,
    fontSize: 17,
  },
  error: { fontSize: 15 },
});
