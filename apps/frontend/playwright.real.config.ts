import { defineConfig, devices } from '@playwright/test'

/**
 * モックを実物に差し替えて 1 本通すための設定（IT1 の Try 8）。
 *
 * 2 とおりの流し方がある。
 *
 * 1. ローカルにバックエンドを直接起動している場合: そのまま実行する。MSW を無効にした
 *    開発サーバー（`npm run dev:api`）が :3000 で立ち、`/api` を Gateway（:8080）へ中継する
 * 2. kind の統合環境に対して流す場合: `E2E_BASE_URL=http://localhost` を指定する。
 *    このとき開発サーバーは立てず、クラスタが配信しているフロントエンドをそのまま使う
 *
 * <p>2 で開発サーバーを経由してはいけない。Gateway の CORS は kind の Ingress
 * （`http://localhost`）だけを許すため、:3000 から呼ぶと 403 になる。これは設定の誤りではなく、
 * 許していない出所からの呼び出しを断っている正しい動きである。
 */

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000'
const usesDeployedFrontend = process.env.E2E_BASE_URL !== undefined

export default defineConfig({
  testDir: './e2e',
  testMatch: 'real-backend.spec.ts',
  fullyParallel: false,
  workers: 1,
  use: { baseURL, trace: 'off' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  ...(usesDeployedFrontend
    ? {}
    : {
        webServer: {
          command: 'npm run dev:api',
          url: 'http://localhost:3000',
          reuseExistingServer: false,
          timeout: 120_000,
        },
      }),
})
