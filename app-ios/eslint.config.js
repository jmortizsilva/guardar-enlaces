// Configuración de ESLint en formato plano (el que usa ESLint 9).
// Se apoya en las reglas de Expo y deja el formato en manos de Prettier.
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');
const prettierRecomendado = require('eslint-plugin-prettier/recommended');
const globals = require('globals');

module.exports = defineConfig([
  expoConfig,
  prettierRecomendado,
  {
    ignores: ['dist/*', 'node_modules/*', '.expo/*'],
  },
  {
    // Config plugins de Expo: los ejecuta Node directamente durante el
    // prebuild, no pasan por Metro/React Native.
    files: ['plugins/**/*.js'],
    languageOptions: {
      sourceType: 'commonjs',
      globals: globals.node,
    },
  },
]);
