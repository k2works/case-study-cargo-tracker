# ADR-007: Playwright E2E テストで Page Object Model パターンを採用する

ブラウザ E2E テストに Playwright + TypeScript を使用し、Page Object Model パターンでテストコードを構造化する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

ユーザー操作フロー・画面遷移を検証するブラウザ E2E テストが必要である。

- Spring Boot アプリケーション（Thymeleaf + htmx）の画面操作を自動テストしたい
- テストコードの保守性を確保するため、画面操作とテストロジックを分離したい
- API E2E テスト（MockMvc）では検証できない JavaScript・CSS の動作を確認したい

## 決定

**Playwright 1.44+ (TypeScript) を `apps/cargo-tracker/e2e/` に配置し、Page Object Model パターンを採用する。**

### 変更箇所

- `apps/cargo-tracker/e2e/` に Playwright プロジェクトを作成
- `apps/cargo-tracker/e2e/src/pages/` に Page Object クラスを配置
- `apps/cargo-tracker/e2e/src/tests/` にテストスペックを配置
- `apps/cargo-tracker/e2e/src/fixtures.ts` で共通フィクスチャ（ログイン等）を定義
- `playwright.config.ts` で Chromium ブラウザ・`baseURL: http://localhost:8080` を設定

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| Selenium | Playwright の方が高速で API が現代的 |
| Cypress | サーバーサイドレンダリング（Thymeleaf）との相性が Playwright の方が良い |
| cargo-tracker 内に Playwright を含める | Java プロジェクトと Node.js プロジェクトの依存関係を分離するため独立配置 |

## 影響

### ポジティブ

- Page Object パターンにより画面変更時の修正箇所が Page クラスに局所化される
- フィクスチャにより認証等の共通処理を再利用できる
- 失敗時にスクリーンショット・動画・トレースが自動保存される

### ネガティブ

- テスト実行前に cargo-tracker を手動起動する必要がある
- ブラウザインストールが必要（CI では `--with-deps` オプション）

## コンプライアンス

- cargo-tracker 起動状態で `cd apps/cargo-tracker/e2e && npm test` が全テスト通過すること
- CI ワークフロー（`.github/workflows/ci.yml`）の E2E ジョブが通過すること

## 備考

- 関連コミット: `222be0d`
- 関連 ADR: ADR-006
