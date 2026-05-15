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
  // 同一バックエンド（共有 DB + 共有 admin ユーザー + Axon Server 単一インスタンス）に
  // 対して複数シナリオが並列で書き込み・購読すると、結果整合性の Read Model 反映が
  // 競合してタイミング起因の flake を起こすので、E2E はすべてシーケンシャルに走らせる。
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  // テスト全体の上限時間。Axon Server の Event Processor 起動遅延を吸収する。
  timeout: 60_000,
  expect: {
    // PooledStreamingEventProcessor は Token 取得後に Read Model を反映するため
    // 初回イベントでは数秒の遅延がある。デフォルト 5s では不足することがあるので 15s。
    timeout: 15_000,
  },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',

  use: {
    baseURL: 'http://localhost:3000',
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

  // Vite dev サーバーを自動起動（バックエンドは別途手動起動）
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
