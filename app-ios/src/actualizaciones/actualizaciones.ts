import * as SecureStore from 'expo-secure-store';
import * as Updates from 'expo-updates';
import { Alert } from 'react-native';

import { anunciarImportante } from '../accesibilidad/anuncios';

const CLAVE_ULTIMO_ID_VISTO = 'ultimoUpdateIdVisto';

/**
 * Editar en cada publicación con cambios visibles para quien usa la app.
 * Se muestra una vez, la primera vez que arranca con un Updates.updateId
 * distinto al último visto.
 */
export const NOVEDADES =
  'La aplicación ya no pide cuenta para empezar: se abre directa en la lista ' +
  'y funciona entera en este iPhone. Entrar con Google, desde Ajustes, sirve ' +
  'para tener los mismos enlaces en el PC; al entrar te pregunta si quieres ' +
  'subir los que ya tenías aquí.';

let comprobando = false;

/**
 * Busca actualizacion OTA y, si hay, pregunta antes de instalar. Patron de
 * comun/docs/GUIA-ENTORNO-IOS.md "Avisar al usuario y actualizar en caliente"
 * (probado en Mopi): nunca descarga ni reinicia sin permiso explicito.
 */
export async function comprobarActualizacion(opciones: { manual?: boolean } = {}): Promise<void> {
  if (!Updates.isEnabled || comprobando) return;
  comprobando = true;
  try {
    const resultado = await Updates.checkForUpdateAsync();
    if (resultado.isAvailable) {
      Alert.alert('Nueva versión disponible', 'La app se reiniciará. ¿Instalar ahora?', [
        { text: 'Ahora no', style: 'cancel' },
        { text: 'Instalar', onPress: instalarActualizacion },
      ]);
    } else if (opciones.manual) {
      Alert.alert('Actualizada', 'Ya tienes la última versión.');
    }
  } catch (error) {
    if (opciones.manual) {
      Alert.alert('No se pudo comprobar', String(error));
    }
  } finally {
    comprobando = false;
  }
}

async function instalarActualizacion(): Promise<void> {
  try {
    anunciarImportante('Descargando actualización…');
    await Updates.fetchUpdateAsync();
    await Updates.reloadAsync();
  } catch (error) {
    anunciarImportante('No se pudo instalar la actualización');
    Alert.alert('No se pudo instalar', String(error));
  }
}

/**
 * Al primer arranque con un Updates.updateId nuevo, avisa de las novedades.
 * En la primera instalación (sin id previo guardado) solo memoriza el id,
 * sin avisar.
 */
export async function avisarSiHayNovedades(): Promise<void> {
  if (!Updates.isEnabled || !Updates.updateId) return;
  const anterior = await SecureStore.getItemAsync(CLAVE_ULTIMO_ID_VISTO);
  if (anterior === Updates.updateId) return;
  await SecureStore.setItemAsync(CLAVE_ULTIMO_ID_VISTO, Updates.updateId);
  if (anterior) {
    Alert.alert('Novedades', NOVEDADES);
  }
}
