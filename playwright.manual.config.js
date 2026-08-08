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
export default defineConfig({
  testDir: './e2e/manual',
  // 画面遷移の前提（ログイン・荷主の登録）が順序に依存するため直列で実行する
  workers: 1,
  fullyParallel: false,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:8080',
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
    command: 'cd apps/cargo-tracker && ./gradlew bootRun -PincludeH2=true --args=--spring.profiles.active=local',
    url: 'http://localhost:8080/login',
    // **既存サーバを使い回さない。** 使い回すと、前に起動したままの古いアプリで
    // 撮影してしまい、UI を変更したのにキャプチャだけ古いという状態に気づけない。
    // ポートが塞がっていればここで失敗させる。
    reuseExistingServer: false,
    timeout: 180_000,
  },
});
