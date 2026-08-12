import { defineConfig, devices } from '@playwright/test';

/**
 * E2E テストの設定（IT6 で有効化。`development_strategy.md` の品質ゲート）。
 *
 * **マニュアルのキャプチャ生成（playwright.manual.config.js）とは目的が違う。**
 * あちらはドキュメント生成であり検証しない。こちらは
 * **「業務価値が実際に成立するか」を確かめる最終ゲート**である。
 *
 * テストピラミッド上 E2E は最小に留める（`test_strategy.md`）。単体・統合で
 * 確かめられることをここで繰り返さない。ここで確かめるのは、
 * **複数のロールをまたいで一本の業務が通ること**だけである。
 *
 * 実行は `npm run e2e` で明示的に行う。CI では backend-ci の別ジョブが呼ぶ。
 */
/**
 * E2E 専用のポート。
 *
 * **開発サーバ（8080）と分ける。** 同じポートを使うと、IDE でアプリを起動したまま
 * E2E を回そうとしたときに「ポートが使われている」で止まる。かといって
 * 既存サーバを使い回すと、**古いアプリに対して緑になり変更が壊れていることに
 * 気づけない**（下記 reuseExistingServer の理由）。
 *
 * 分けておけば、開発サーバを落とさずに E2E を回せる。
 */
const E2E_PORT = Number(process.env.E2E_PORT ?? 18080);

export default defineConfig({
  testDir: './e2e/app',
  // 業務の流れは順序に依存する（確定してからでないと追跡番号は発行できない）。
  // **並列にすると、同じ航海の空き容量を奪い合って落ちる。**
  workers: 1,
  fullyParallel: false,
  // 落ちた理由を追えるようにする。**再実行で緑になるのを待たない**（retries: 0）
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${E2E_PORT}`,
    locale: 'ja-JP',
    viewport: { width: 1280, height: 800 },
    // 落ちたときだけ残す。常に残すと、緑の実行でも成果物が積み上がる
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: {
    // local プロファイル（H2）で起動する。**本番環境へは接続しない。**
    //
    // E2E を実 PostgreSQL で回さないのは、ここで確かめるのが SQL の正しさでは
    // ないためである（それは Testcontainers の仕事。ADR-003）。
    // ただし **H2 で動くことは PostgreSQL で動く保証にならない**ため、
    // 全クエリの方言差は H2DialectSmokeTest が別に見ている。
    command:
      'cd apps/cargo-tracker && ./gradlew bootRun -PincludeH2=true '
      // **動作確認用データは入れない**（IT19）。開発環境の起動では
      // マニュアルと同じ状態まで自動で作るが、E2E では切る。
      //
      // **投入は起動後に走る。** 画面が応答し始めた時点ではまだ作り終えておらず、
      // シナリオと同時に動く —— 一覧の先頭に自分が登録した作業が出ることを
      // 確かめるテストが、**投入した作業に押し出されて落ちた**（CI で実測）。
      //
      // **シナリオは自分で前提を作る。** 前提をシードに預けると、
      // シードを変えた瞬間にシナリオが壊れる。
      + `--args='--spring.profiles.active=local --server.port=${E2E_PORT}`
      + " --cargo-tracker.demo.install=false'",
    url: `http://localhost:${E2E_PORT}/login`,
    // **既存サーバを使い回さない。** 使い回すと、古いアプリに対して緑になり、
    // 変更が壊れていることに気づけない
    reuseExistingServer: false,
    timeout: 180_000,
  },
});
