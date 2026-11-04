import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    // 開発時は、バックエンド（ローカルの結線）へ中継する。値は環境で変えられる
    proxy: {
      '/command/graphql': process.env.VITE_BACKEND_URL ?? 'http://127.0.0.1:8080',
      '/query/graphql': process.env.VITE_BACKEND_URL ?? 'http://127.0.0.1:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/testing/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    testTimeout: 20000,
  },
});
