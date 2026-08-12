import { defineConfig, devices } from '@playwright/test';

/**
 * ユーザーマニュアルの画面キャプチャ生成専用の設定。
 *
 * **これはテストではなくドキュメント生成である。** 検証を行わないため、
 * 通常のテスト実行（`./gradlew check`）からは独立している。
 * 実行は `npm run screenshots:manual` で明示的に行う。
 *
 * キャプチャを手作業で `docs/manual/assets/` に置いてはいけない。
 * 置くと次回の再生成で上書きされ、UI 変更のたびに撮り漏れが出る。
 */
/**
 * キャプチャ専用のポート。
 *
 * **開発サーバ（8080）と分ける。** 同じポートを使うと、IDE でアプリを起動したまま
 * 撮影しようとしたときに「ポートが使われている」で止まる。かといって
 * 既存サーバを使い回すと、**古いアプリで撮影してしまい、UI を変更したのに
 * キャプチャだけ古いという状態に気づけない**（下記 reuseExistingServer の理由）。
 */
const MANUAL_PORT = Number(process.env.MANUAL_PORT ?? 18081);

export default defineConfig({
  testDir: './e2e/manual',
  // 画面遷移の前提（ログイン・荷主の登録）が順序に依存するため直列で実行する
  workers: 1,
  fullyParallel: false,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${MANUAL_PORT}`,
    locale: 'ja-JP',
    // **日付欄の表記はロケールとタイムゾーンで変わる。** 指定しないと
    // `datetime-local` が英語ロケール（mm/dd/yyyy・12 時間制）で写り、
    // 読者の画面と一致しない図がマニュアルに残る
    timezoneId: 'Asia/Tokyo',
    // マニュアルの図の横幅を揃える。00-はじめに.md の推奨画面幅と合わせる
    viewport: { width: 1280, height: 800 },
    ...devices['Desktop Chrome'],
  },
  webServer: {
    // local プロファイル（H2）で起動する。**本番環境へは接続しない。**
    // マニュアルは Git 管理下で公開されるため、実在の取引先情報が残るのを避ける。
    // **支払期限を過去にして起動する。** 督促対象一覧のキャプチャは
    // 「対象が並んだ状態」でなければ何も伝えない。**撮影のためだけに
    // 本番の経路へ細工を足さない** — 設定値で作れることは設定値で作る。
    command: 'cd apps/cargo-tracker && ./gradlew bootRun -PincludeH2=true '
      + `--args='--spring.profiles.active=local --server.port=${MANUAL_PORT}`
      + ' --cargo-tracker.billing.payment-term-days=-1'
      // **動作確認用データは入れない**（IT19）。開発環境の起動では
      // マニュアルと同じ状態まで自動で作るが、撮影ではそれを切る。
      // 入れたままだと **「見積がまだありません」の図が撮れない** ——
      // 最初に開く人が必ず見る画面であり、図が要る。
      // 各 spec が自分で前提を作るため、**撮影の再現性はこちらのほうが高い**。
      + " --cargo-tracker.demo.install=false'",
    url: `http://localhost:${MANUAL_PORT}/login`,
    // **既存サーバを使い回さない。** 使い回すと、前に起動したままの古いアプリで
    // 撮影してしまい、UI を変更したのにキャプチャだけ古いという状態に気づけない。
    // ポートが塞がっていればここで失敗させる。
    reuseExistingServer: false,
    timeout: 180_000,
  },
});
