import { defineConfig, devices } from '@playwright/test'

/**
 * E2E テストの設定。
 *
 * デモ項目（イテレーション計画）をそのまま通すことをイテレーションの受け入れ基準とするため、
 * E2E は「動いたことの確認」ではなく「業務価値が成立することの最終ゲート」として置く。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    // 日時は業務タイムゾーンで判断する。toISOString() 由来の値を使うと
    // CI（UTC）で日付が 1 日ずれ、E2E だけが CI で落ちる。
    timezoneId: 'Asia/Tokyo',
    locale: 'ja-JP',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // バックエンド未実装の間は API モックを有効にした dev サーバーで検証する。
    // Day 10 で実物のバックエンドに差し替える。
    command: 'npm run dev:mock',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
