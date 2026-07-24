import { defineConfig, devices } from '@playwright/test';

// E2E は起動済みのアプリ（PORT=8092）に対して実行する。
// ローカルでは `make watch` 等でサーバーを起動し、DB は `docker compose up -d postgres` を前提とする。
// CI では webServer でビルド済みバイナリを起動する（BASE_URL 上書き可）。
const baseURL = process.env.BASE_URL || 'http://localhost:8092';

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
});
