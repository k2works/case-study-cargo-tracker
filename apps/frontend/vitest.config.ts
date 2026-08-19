import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    // E2E は Playwright が実行する。除外しないと vitest が jsdom 上で拾い、
    // 「E2E が通った」ように見えたまま実際のブラウザ挙動を誰も検証しなくなる。
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      // lcov は SonarQube 連携に必要。渡さないとカバレッジ 0% と表示され、
      // 品質ゲートのカバレッジ条件が常に赤になる
      reporter: ['text', 'html', 'lcov'],
      exclude: ['src/mocks/**', 'src/test/**', '**/*.config.ts', 'src/main.tsx'],
    },
  },
})
