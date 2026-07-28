import { defineConfig } from 'vitest/config';
import swc from 'unplugin-swc';

export default defineConfig({
  plugins: [
    // NestJS のデコレータメタデータ（emitDecoratorMetadata 相当）を有効化する
    swc.vite({
      module: { type: 'es6' },
      jsc: {
        target: 'es2023',
        transform: {
          legacyDecorator: true,
          decoratorMetadata: true,
          react: {
            runtime: 'automatic',
            importSource: 'react',
          },
        },
        parser: {
          syntax: 'typescript',
          decorators: true,
          tsx: true,
        },
      },
    }),
  ],
  test: {
    globals: true,
    environment: 'node',
    // supertest + express-session の統合テストでフルスイート時に稀に発生する
    // ログインセッションのタイミング起因フレークを吸収する（恒久対応は retrospective-2 Try）
    retry: 1,
    include: ['src/**/*.{test,spec}.{ts,tsx}', 'test/**/*.{test,spec}.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.{test,spec}.{ts,tsx}', 'src/main.ts', 'src/views/**'],
    },
  },
});
