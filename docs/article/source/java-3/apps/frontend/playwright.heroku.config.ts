import { defineConfig, devices } from '@playwright/test'

/**
 * 開発環境（Heroku）に対する確認。
 *
 * 実行前に `DEV_FRONTEND_URL` を設定する。
 * 例: DEV_FRONTEND_URL=$(heroku apps:info -a take7-frontend --json | jq -r .app.web_url)
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: /(heroku-check|docs-check)\.spec\.ts/,
  workers: 1,
  timeout: 90_000,
  use: { trace: 'off' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
