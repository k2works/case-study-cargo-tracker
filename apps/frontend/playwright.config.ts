import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright 設定（US-UI-r フロントエンド E2E）
 *
 * 実行前提:
 *   - authms（:8081）、bookingms（:8082）、gatewayms（:8080）が手動起動済み
 *   - Vite dev サーバー（:3000）は本 config の webServer で自動起動
 *
 * 詳細は e2e/README.md を参照。
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',

  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // Vite dev サーバーを自動起動（バックエンドは別途手動起動）
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
