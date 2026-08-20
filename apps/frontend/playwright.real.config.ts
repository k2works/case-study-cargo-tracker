import { defineConfig, devices } from '@playwright/test'

/**
 * モックを実物に差し替えて 1 本通すための設定（IT1 の Try 8）。
 *
 * 実行前に Gateway（8080）・authms（8081）・bookingms（8082）を起動しておく。
 * MSW を無効にした開発サーバー（`npm run dev:api`）に対して流す。
 */

export default defineConfig({
  testDir: './e2e',
  testMatch: 'real-backend.spec.ts',
  fullyParallel: false,
  workers: 1,
  use: { baseURL: 'http://localhost:3000', trace: 'off' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev:api',
    url: 'http://localhost:3000',
    reuseExistingServer: false,
    timeout: 120_000,
  },
})
