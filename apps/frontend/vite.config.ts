import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

/**
 * Vite 設定。
 *
 * 開発サーバは 5173 番ポートで起動し、`/api` 配下のリクエストは
 * gatewayms（8080）にプロキシする。各サービスのバックエンドへ直接
 * アクセスする場合は VITE_AUTH_API_BASE_URL / VITE_ROUTING_API_BASE_URL を
 * .env.local に定義する。
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['e2e/**', 'node_modules/**'],
  },
});
