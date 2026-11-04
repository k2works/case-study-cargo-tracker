import { defineConfig, devices } from '@playwright/test';

// バックエンドは、使い捨ての PostgreSQL（Testcontainers）を立てるテスト用の起動口（E2eServer）。接続情報は持たない（R-10）。
const BACKEND_PORT = 18080;
const WEB_PORT = 15173;

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  use: { baseURL: `http://127.0.0.1:${WEB_PORT}`, trace: 'retain-on-failure', locale: 'ja-JP', timezoneId: 'Asia/Tokyo' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `cd ../.. && sbt -Dsbt.supershell=false "local-server/Test/runMain cargotracker.app.E2eServer ${BACKEND_PORT}"`,
      url: `http://127.0.0.1:${BACKEND_PORT}/health`,
      reuseExistingServer: true,
      timeout: 240_000,
    },
    {
      command: `npx vite --port ${WEB_PORT} --strictPort --host 127.0.0.1`,
      url: `http://127.0.0.1:${WEB_PORT}`,
      reuseExistingServer: true,
      env: {
        VITE_BACKEND_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        VITE_DEV_ROLE: 'Sales',
        VITE_DEV_USER_ID: '01ARZ3NDEKTSV4RRFFQ69G5FAV',
      },
    },
  ],
});
