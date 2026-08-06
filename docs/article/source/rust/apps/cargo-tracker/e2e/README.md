# デモ項目 E2E テスト（Playwright）

Cargo Tracker のイテレーションデモ項目を実ブラウザ（Chromium）で自動実行する E2E テストです。SSR（Askama）+ htmx + tower-sessions のフローを、実 Rust サーバ・実 PostgreSQL 上で検証します。

## 構成

| ファイル | 役割 |
| :--- | :--- |
| `playwright.config.ts` | Playwright 設定。`webServer` で `run-app.sh` を起動し `/health` を待機。共有 DB を変更するため直列実行（workers=1） |
| `run-app.sh` | 一時 Postgres（ポート 55440）起動 → マイグレーション + デモシード投入 → Rust サーバ（8080）を exec 起動 |
| `global-teardown.ts` | 終了時に一時 Postgres コンテナを破棄 |
| `tests/helpers.ts` | ログイン・ナビゲーション・予約作成などの共通ヘルパ |
| `tests/it1-demo.spec.ts` | IT1 デモ（予約基盤: 認証/RBAC・荷主登録・予約登録・必須検証） |
| `tests/it2-demo.spec.ts` | IT2 デモ（航海スケジュール: 登録・更新・検索） |
| `tests/it3-demo.spec.ts` | IT3 デモ（経路算出・選択: 貨物仕様・推奨順候補・確定） |
| `tests/it4-demo.spec.ts` | IT4 デモ（予約状態機械: 依頼・調整・紐付け・通知・確定・差戻し・キャンセル） |

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

## カバーするデモ項目

いずれもナビゲーションメニュー（ダッシュボード → 貨物予約/航路管理 → 一覧 → 詳細/編集）経由で操作する。

### IT1（予約基盤）

| ケース | 検証内容 |
| :--- | :--- |
| 認証 | 未認証は `/login` へリダイレクト |
| RBAC | ロール別ナビ出し分け（営業=見積管理、経路設計者=航路管理）・ログアウト |
| 荷主登録 | 個人・法人（割引率付き）荷主の登録成功 |
| 予約登録 | 一般貨物の予約が仮予約（PRELIMINARY）で登録される |
| 必須検証 | 危険物で申告欠落だとエラー表示 |

### IT2（航海スケジュール・別レーン JPTYO→USNYC）

| ケース | 検証内容 |
| :--- | :--- |
| US24 | 航路一覧から航海（V9001）を新規登録 |
| US25 | 登録済み航海を呼び出して更新（船名変更） |
| US07 | 出発地・目的地・貨物種別で検索、危険物では該当なし |

### IT3（経路算出・選択・自前作成の予約）

| ケース | 検証内容 |
| :--- | :--- |
| US07/US08/US09 | 貨物仕様確認 → 推奨順（★1・直行）候補 → 確定（経路提案中へ） |
| US08 | 期限内に到達できない場合は期限超過候補（⚠）を警告表示 |

### IT4（予約状態機械・シード予約）

| ケース | 検証内容 | 使用予約 |
| :--- | :--- | :--- |
| US06 | 経路設計依頼で 仮受付 → 経路設計中 | BKG-0001 |
| US10 | 期限超過（⚠）のみの候補を条件調整（期限延長）で期限内に再算出 | BKG-0005 |
| US11 | 経路確定・紐付けで 経路設計中 → 経路提案中・選択ルート表示 | BKG-0004 |
| US13 | 経路提案中 → 経路設計中 への差し戻し | BKG-0004 |
| US12/US13 | 荷主通知（状態不変）→ 予約確定（経路提案中 → 予約確定） | BKG-0002 |
| US13 | 経路設計中の予約のキャンセル | BKG-0005 |

IT1・IT3 はデモに必要な予約を UI から自前で作成し、IT2 は別レーンの航海を用いることで、共有 DB 上で相互干渉しないよう設計している。シードデータは `crates/cargo-tracker-server/src/bin/seed.rs` が用意します。
