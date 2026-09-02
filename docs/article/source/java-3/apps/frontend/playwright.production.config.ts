import { defineConfig, devices } from '@playwright/test'

/**
 * 本番相当ビルドに対する検査。
 *
 * 開発サーバでは動作確認用ログインが有効なため、本番でそれが無効であることは
 * ビルド成果物を配信して確かめるほかない。
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: 'production-build.spec.ts',
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4174',
    timezoneId: 'Asia/Tokyo',
    locale: 'ja-JP',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // ビルド引数を渡さない = 本番と同じ条件
    command: 'npm run build && npm run preview -- --port 4174',
    url: 'http://localhost:4174',
    reuseExistingServer: false,
    timeout: 180_000,
  },
})
