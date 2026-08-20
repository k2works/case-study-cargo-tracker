import { defineConfig, devices } from '@playwright/test'

/**
 * 開発環境の画面キャプチャ生成。
 *
 * 動作確認用ログインを有効にしたビルドを配信する。本番相当ビルドで撮る通常のキャプチャ
 * （playwright.manual.config.ts）とは別の設定にしないと、業務環境の説明に
 * 「開発環境です」の帯が写り込む。
 */
export default defineConfig({
  testDir: './e2e/manual-dev',
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4175',
    viewport: { width: 1280, height: 800 },
    deviceScaleFactor: 2,
    timezoneId: 'Asia/Tokyo',
    locale: 'ja-JP',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run build && npm run preview -- --port 4175',
    env: { VITE_ENABLE_API_MOCK: 'true', VITE_DEMO_LOGIN_ENABLED: 'true' },
    url: 'http://localhost:4175',
    reuseExistingServer: false,
    timeout: 180_000,
  },
})
