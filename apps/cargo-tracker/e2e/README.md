# デモ項目 E2E テスト（Playwright）

Cargo Tracker のイテレーションデモ項目を実ブラウザ（Chromium）で自動実行する E2E テストです。SSR（Askama）+ htmx + tower-sessions のフローを、実 Rust サーバ・実 PostgreSQL 上で検証します。

## 構成

| ファイル | 役割 |
| :--- | :--- |
| `playwright.config.ts` | Playwright 設定。`webServer` で `run-app.sh` を起動し `/health` を待機。共有 DB を変更するため直列実行（workers=1） |
| `run-app.sh` | 一時 Postgres（ポート 55440）起動 → マイグレーション + デモシード投入 → Rust サーバ（8080）を exec 起動 |
| `global-teardown.ts` | 終了時に一時 Postgres コンテナを破棄 |
| `tests/demo.spec.ts` | IT4 デモ項目（予約状態機械）の E2E |

## 前提

- Docker（一時 Postgres 用）
- Node.js（Playwright 実行）
- 初回のみ依存とブラウザを取得:

```bash
cd apps/cargo-tracker/e2e
npm install
npx playwright install chromium
```

## 実行

```bash
cd apps/cargo-tracker/e2e
npm test              # ヘッドレス実行
npm run test:headed   # ブラウザ表示で実行
npm run report        # 直近のレポートを表示
```

`npm test` は自動で一時 Postgres 起動 → デモシード投入 → サーバ起動 → テスト実行 → 一時 DB 破棄まで行います。開発用 DB（ポート 5432）には影響しません。

## カバーするデモ項目（IT4・予約状態機械）

| ケース | 検証内容 | 使用予約 |
| :--- | :--- | :--- |
| US06 | 経路設計依頼で 仮受付 → 経路設計中 | BKG-0001 |
| US10 | 期限超過（⚠）のみの候補を条件調整（期限延長）で期限内に再算出 | BKG-0005 |
| US11 | 経路確定・紐付けで 経路設計中 → 経路提案中・選択ルート表示 | BKG-0004 |
| US13 | 経路提案中 → 経路設計中 への差し戻し | BKG-0004 |
| US12/US13 | 荷主通知（状態不変）→ 予約確定（経路提案中 → 予約確定） | BKG-0002 |
| US13 | 経路設計中の予約のキャンセル | BKG-0005 |

シードデータは `crates/cargo-tracker-server/src/bin/seed.rs` が用意します。
