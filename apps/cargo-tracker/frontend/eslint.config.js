import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import importPlugin from 'eslint-plugin-import';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'playwright-report', 'test-results'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      import: importPlugin,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,

      // 機能どうしが直接 import し合うと、1 つ直すたびに全部を読む羽目になる。
      // 機能をまたぐものは shared に上げる。
      'import/no-restricted-paths': [
        'error',
        {
          zones: [
            {
              target: './src/features',
              from: './src/features',
              message:
                '機能どうしを直接 import しない。共通のものは src/shared に上げる',
            },
            {
              target: './src/shared',
              from: './src/features',
              message: 'shared は features を知らない（依存の向きは features → shared）',
            },
            {
              target: './src/shared',
              from: './src/app',
              message: 'shared は app を知らない',
            },
          ],
        },
      ],
    },
    settings: {
      // 解決は相対パスで足りる。TypeScript リゾルバは版の食い違いで
      // 「Resolve error」を出し、規則そのものが働かなくなる。
      'import/resolver': {
        node: { extensions: ['.js', '.jsx', '.ts', '.tsx'] },
      },
    },
  },
);
