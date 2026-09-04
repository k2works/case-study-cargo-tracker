import { defineConfig, devices } from '@playwright/test';

/**
 * E2E は「画面から踏めるか」だけを見る。業務の細かい分岐は受け入れテスト
 * （Cucumber）と単体で固定し、ここでは到達性と反映中の見え方に絞る。
 */
export default defineConfig({
  testDir: './e2e',
  // キャプチャ生成は CI の到達性スモークとは別に回す（画面を変えたときだけ）。
  // クラスタ確認は E2E_BASE_URL があるときだけ。無いときに読み込むと、
  // 中で skip していても「0 件で緑」に見える回が混じる。
  testIgnore: process.env.MANUAL_CAPTURE
    ? []
    : [
        '**/manual-capture.spec.ts',
        ...(process.env.E2E_BASE_URL ? [] : ['**/cluster.spec.ts']),
      ],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  // CI で 1 度でも落ちたら赤にする。再試行で緑にすると、たまに落ちるテストを
  // 本物の赤と区別できなくなる。
  retries: 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env.CI,
        timeout: 60_000,
      },
});
