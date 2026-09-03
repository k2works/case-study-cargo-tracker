// Vitest の設定を同じファイルに書くので vitest/config の defineConfig を使う。
// vite の defineConfig だと test キーが型で弾かれる。
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': new URL('./src', import.meta.url).pathname },
  },
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
    coverage: {
      provider: 'v8',
      // SonarQube に渡す形。出さないとカバレッジ 0% として「新規コードが
      // 基準を満たさない」で落ちるか、逆に測っていないまま緑になる。
      reporter: ['text-summary', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/test/**', 'src/**/*.test.{ts,tsx}', 'src/main.tsx'],
    },
  },
});
