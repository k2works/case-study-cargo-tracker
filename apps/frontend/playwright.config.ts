import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright 設定（IT1 フロントエンド E2E）
 *
 * 実行前提:
 *   - authms（:8081）、routingms（:8083）、gatewayms（:8080）が手動起動済み
 *   - Vite dev サーバー（:5173）は本 config の webServer で自動起動
 *
 * 同一バックエンドに対して複数シナリオが並列で書き込むと
 * 結果整合性の競合が起きるため、シーケンシャル実行とする。
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  timeout: 60_000,
  expect: {
    timeout: 15_000,
  },
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never' }]]
    : 'list',

  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
