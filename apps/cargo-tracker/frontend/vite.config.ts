import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // 開発中は Gateway 経由で叩く。フロントは各サービスの URL を知らない。
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // E2E は Playwright が回すので Vitest の対象から外す。
    exclude: ['e2e/**', 'node_modules/**'],
  },
});
