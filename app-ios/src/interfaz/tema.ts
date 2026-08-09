/**
 * Paleta y medidas. Los colores cumplen el contraste mínimo AA (4,5:1) sobre
 * su fondo, en claro y en oscuro.
 */

import { useColorScheme } from 'react-native';

export type Tema = {
  fondo: string;
  superficie: string;
  texto: string;
  textoSecundario: string;
  acento: string;
  textoSobreAcento: string;
  peligro: string;
  exito: string;
  borde: string;
};

const CLARO: Tema = {
  fondo: '#FFFFFF',
  superficie: '#F2F2F7',
  texto: '#11181C',
  textoSecundario: '#4A5056',
  acento: '#0A57C2',
  textoSobreAcento: '#FFFFFF',
  peligro: '#B3261E',
  exito: '#1B6E3C',
  borde: '#C9CDD1',
};

const OSCURO: Tema = {
  fondo: '#000000',
  superficie: '#1C1C1E',
  texto: '#F5F6F7',
  textoSecundario: '#B6BBC0',
  acento: '#7FB0FF',
  textoSobreAcento: '#0B1220',
  peligro: '#FF9A92',
  exito: '#74D391',
  borde: '#3A3A3C',
};

export function useTema(): Tema {
  return useColorScheme() === 'dark' ? OSCURO : CLARO;
}

/** Altura mínima de cualquier elemento pulsable (guía de Apple: 44 pt). */
export const ALTURA_MINIMA_TOQUE = 48;

export const ESPACIADO = { pequeno: 8, medio: 16, grande: 24 } as const;
