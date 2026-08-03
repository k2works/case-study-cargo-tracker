import { defineConfig } from '@playwright/test';

/**
 * E2E テスト設定（TS05b）。
 *
 * **HTTP 統合テストとの役割の違い**: `apps/cargo-tracker/test/integration/**` は
 * サーバを起動して HTTP を縦断するが、**ブラウザは介さない**。htmx による
 * 差し替え・フォームの実送信・Cookie のブラウザ側の扱いは検証できない。
 * E2E はそこを見る。重ねて同じことを確かめるのではなく、**別の層の壊れ方**を扱う。
 *
 * アプリは fat JAR を H2 で起動する（`DATABASE_URL` 未設定 = 開発 = 開発用
 * サンプルデータを投入）。PostgreSQL を要求しないのは、E2E の目的が
 * 画面の操作であって方言差の検出ではないためである（方言差は日次ジョブが見る）。
 */
export default defineConfig({
  testDir: './e2e',
  // 画面操作は起動待ちを含むため、統合テストより長めに取る
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // **CI では失敗を再試行しない**。まぐれで通ったものを緑と呼ばない
  retries: 0,
  // シナリオ間で DB を共有するため直列に実行する。並列にすると、
  // あるシナリオが登録した荷主が別のシナリオの一覧に現れて数が合わなくなる
  workers: 1,
  fullyParallel: false,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: 'http://localhost:8081',
    // 失敗時の追跡材料を残す。**再現しない失敗**に対して、記録だけで
    // 原因へ辿り着けるようにする（IT4・IT5 の間欠異常の教訓）
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'java -jar artifact/cargo-tracker.jar',
    // **作業ディレクトリを合わせる**。マイグレーションの探索先には
    // `filesystem:resources/db/migration`（相対パス）が含まれるため、
    // 別の場所から起動すると「適用 0 件」でテーブルが無いまま立ち上がる。
    // E2E を書いて初めて分かった依存であり、統合テストは同じ JVM 内で
    // 動くため気付けなかった
    cwd: 'apps/cargo-tracker',
    url: 'http://localhost:8081/health/ready',
    env: { PORT: '8081', APP_ENV: 'development' },
    // 既存のサーバがあれば使う（手元での反復を速くする）。CI では常に起動する
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
