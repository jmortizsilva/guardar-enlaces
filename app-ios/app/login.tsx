import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { anunciarImportante } from '../src/accesibilidad/anuncios';
import { EnlacesEnElTelefono, useSesion } from '../src/contexto/ProveedorApp';
import { Boton } from '../src/interfaz/Boton';
import { ESPACIADO, useTema } from '../src/interfaz/tema';

/**
 * Entrar con Google para sincronizar (ver backend/docs/CONTRATO-API.md). El
 * alta es abierta: entrar la primera vez ES crear la cuenta.
 *
 * No es un porton: a esta pantalla se llega desde Ajustes y la app funciona
 * entera sin pasar por aqui. Lo unico que da la cuenta es sincronizar con el
 * PC; por eso lo dice el texto, en vez de pedir el inicio de sesion a secas.
 *
 * El consentimiento se abre en una hoja del sistema (ASWebAuthenticationSession),
 * fuera de la app: iOS pregunta antes si se permite usar google.com para iniciar
 * sesion. Por eso el boton avisa de que se va a abrir Safari.
 */
export default function Login() {
  const tema = useTema();
  const sesion = useSesion();
  const [entrando, setEntrando] = useState(false);
  const [error, setError] = useState('');

  /**
   * Si al entrar hay enlaces en el telefono que no son de esta cuenta, decide
   * el usuario. Se dice cuantos son y que pasa con cada respuesta: ninguna de
   * las dos se puede deshacer.
   */
  const decidirImportacion = ({ cuantos, deOtraCuenta }: EnlacesEnElTelefono) =>
    new Promise<boolean>((resolver) => {
      const cuenta = cuantos === 1 ? '1 enlace guardado' : `${cuantos} enlaces guardados`;
      const origen = deOtraCuenta ? 'con otra cuenta' : 'sin cuenta';
      Alert.alert(
        'Enlaces en este iPhone',
        `Hay ${cuenta} en este iPhone ${origen}. ¿Quieres añadirlos a esta cuenta? ` +
          'Si eliges borrarlos, se quitan de este iPhone y no se pueden recuperar.',
        [
          { text: 'Borrarlos', style: 'destructive', onPress: () => resolver(false) },
          { text: 'Añadirlos', onPress: () => resolver(true) },
        ],
        { cancelable: false },
      );
    });

  const alEntrar = async () => {
    setEntrando(true);
    setError('');
    try {
      const resultado = await sesion.iniciarConGoogle(decidirImportacion);
      if (resultado.estado === 'exito') {
        anunciarImportante('Sesión iniciada. Tus enlaces se sincronizarán con el PC.');
        volver();
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
        Entrar con una cuenta
      </Text>
      <Text style={[estilos.aviso, { color: tema.textoSecundario }]}>
        La cuenta sirve para tener los mismos enlaces en el iPhone y en el PC. Sin ella la
        aplicación funciona igual, pero los enlaces se quedan solo en este iPhone.
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
      <Boton etiqueta="Ahora no" variante="secundario" alPulsar={volver} deshabilitado={entrando} />
    </View>
  );
}

/** A esta pantalla se llega desde Ajustes, pero se protege el caso sin historial. */
function volver(): void {
  if (router.canGoBack()) {
    router.back();
  } else {
    router.replace('/');
  }
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
