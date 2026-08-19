import { defineConfig, devices } from '@playwright/test'

/**
 * ユーザーマニュアル用の画面キャプチャ生成。
 *
 * 見た目は本番相当ビルド、データはモックで撮る。開発サーバで撮ると利用者が実際に見る画面と
 * 食い違い、本番環境に接続すると実在の顧客情報がリポジトリに残る。
 */
export default defineConfig({
  testDir: './e2e/manual',
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4173',
    viewport: { width: 1280, height: 800 },
    deviceScaleFactor: 2,
    timezoneId: 'Asia/Tokyo',
    locale: 'ja-JP',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // 本番相当ビルドを配信する。API はモックを有効にしてバックエンド無しで撮る
    command: 'VITE_ENABLE_API_MOCK=true npm run build && npm run preview -- --port 4173',
    url: 'http://localhost:4173',
    reuseExistingServer: false,
    timeout: 180_000,
  },
})
