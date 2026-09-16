import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import importPlugin from 'eslint-plugin-import';
import fs from 'node:fs';

// 機能ディレクトリを実際に読む。手で並べると、機能を足したときに
// 書き忘れたものだけが検査されないまま残る。
const FEATURES = fs
  .readdirSync('./src/features', { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name);

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'playwright-report', 'test-results', 'coverage'] },
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
            // 機能どうしは直接 import しない。ただし同じ機能の中は自由。
            // 一律に禁じると自分のファイルさえ読めなくなる。
            ...FEATURES.map((feature) => ({
              target: `./src/features/${feature}`,
              from: './src/features',
              except: [`./${feature}`],
              message:
                '機能どうしを直接 import しない。共通のものは src/shared に上げる',
            })),
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
