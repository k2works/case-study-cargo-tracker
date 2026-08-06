import { defineConfig, devices } from '@playwright/test';

// E2E は webServer が自動起動するアプリ（PORT=8092）に対して実行する。
// start-app.sh が PostgreSQL（docker compose）起動・マイグレーション・サーバー起動まで行う。
// 既にサーバーが起動済みならそれを再利用する（reuseExistingServer）。
const port = process.env.PORT || '8092';
const baseURL = process.env.BASE_URL || `http://localhost:${port}`;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'line' : 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'bash ./start-app.sh',
    url: `${baseURL}/healthz`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: 'pipe',
    stderr: 'pipe',
    env: { PORT: port },
  },
});
