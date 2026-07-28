import { defineConfig, devices } from '@playwright/test';

const PORT = 8899;

/**
 * Playwright E2E 設定。
 * ウォーキングスケルトンの「全ナビゲーション + ロール別表示制御」を実ブラウザで検証する。
 * DB は pg-mem（DATABASE_URL 未設定）+ 起動時シードを用いる。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: `http://127.0.0.1:${PORT}`,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `PORT=${PORT} node --import @swc-node/register/esm-register src/main.ts`,
    url: `http://127.0.0.1:${PORT}/health`,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
