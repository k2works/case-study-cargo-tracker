import { defineConfig, devices } from '@playwright/test';

/**
 * Cargo Tracker デモ項目の E2E 設定。
 *
 * webServer が run-app.sh を起動し、一時 Postgres + シード + Rust サーバを立ち上げる。
 * デモは状態機械（予約状態遷移）を伴い共有 DB を変更するため、直列実行（workers=1）する。
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  globalTeardown: './global-teardown.ts',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'bash ./run-app.sh',
    url: 'http://localhost:8080/health',
    reuseExistingServer: false,
    timeout: 240_000,
    // パイプ滞留によるサーバ停止（接続リセット）を避けるため出力は破棄する。
    stdout: 'ignore',
    stderr: 'ignore',
  },
});
