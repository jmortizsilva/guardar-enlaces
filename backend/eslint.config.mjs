import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'datos'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // Scripts que se lanzan sueltos en el servidor, dentro del contenedor (node < fichero):
    // CommonJS y con los globales de Node, no como el codigo del servidor.
    files: ['herramientas/**/*.cjs'],
    languageOptions: {
      sourceType: 'commonjs',
      globals: { require: 'readonly', console: 'readonly' },
    },
    rules: { '@typescript-eslint/no-require-imports': 'off' },
  },
);
