# Cargo Tracker（国際貨物輸送管理システム）

国際貨物の見積・予約・経路設計・荷役・追跡・請求を管理する Web システム。
NestJS モジュラーモノリス + TSX SSR + htmx + Kysely（PostgreSQL / pg-mem）。

## クイックスタート

```bash
cd apps/cargo-tracker
npm install
npm run dev          # 開発サーバー（http://localhost:8080）+ ライブリロード
```

ブラウザで <http://localhost:8080> を開き、下表の開発用アカウントでログインする（`npm run dev` 時は pg-mem 上にシードが自動投入される）。

### 開発用アカウント

ローカル開発（`DATABASE_URL` 未設定 = pg-mem）でのみ自動投入される。パスワードは全ロール共通 `password`。

| ロール | 利用者 ID | パスワード |
| :--- | :--- | :--- |
| 荷主 | `shipper` | `password` |
| 営業担当者 | `sales` | `password` |
| 経路設計者 | `route_designer` | `password` |
| 追跡管理者 | `tracker` | `password` |
| 荷役作業員 | `handler` | `password` |
| 経理担当者 | `billing` | `password` |

> ログイン画面（開発時）には利用者 ID `sales` / パスワード `password` が初期入力され、開発用アカウント一覧も表示される。

## npm scripts

| コマンド | 用途 |
| :--- | :--- |
| `npm run dev` | 開発サーバー（`node --watch` で再起動 + pg-mem + シード + ライブリロード） |
| `npm run serve` | 単発起動（再起動なし）。`DATABASE_URL` を指定すると実 PostgreSQL に接続 |
| `npm run start` | ビルド済み（`dist/`）を実行（本番） |
| `npm run test` | 単体・統合テスト（Vitest） |
| `npm run test:watch` | TDD ウォッチ |
| `npm run test:coverage` | カバレッジ計測（`coverage/lcov.info`） |
| `npm run test:e2e` | E2E（Playwright） |
| `npm run lint` / `format` | ESLint / Prettier |
| `npm run typecheck` | 型検査（tsc --noEmit） |
| `npm run arch` | アーキテクチャ検証（dependency-cruiser） |
| `npm run check` | lint + typecheck + arch |
| `npm run verify` | check + test（コミット前の品質ゲート） |
| `npm run build` | 本番ビルド（tsc） |
| `npm run migrate:up` / `migrate:down` | DB マイグレーション（`DATABASE_URL` 必須） |

プロジェクトルートからは `npm run dev` / `tdd` / `verify` / `dev:e2e`（Gulp 経由）も利用できる。

## DB の切り替え

- **`DATABASE_URL` 未設定**: pg-mem（インメモリ、Docker 不要）。起動時にマイグレーション適用 + デフォルトユーザーをシード（冪等）
- **`DATABASE_URL` 設定**: 実 PostgreSQL（本番・Testcontainers）。SQL 互換性の正は Testcontainers

## ディレクトリ構成

```text
src/
├── contexts/<context>/{domain,application,infrastructure,presentation}   # 境界付けられたコンテキスト
├── shared/                # 共有カーネル・認証基盤（Security）・DB 基盤
├── views/                 # TSX テンプレート（SSR）
├── app.module.ts          # 合成ルート
└── main.ts                # エントリポイント
migrations/                # node-pg-migrate（.sql）
test/                      # 統合テスト（supertest）
e2e/                       # Playwright E2E
```

詳細は [アプリケーション開発環境セットアップ手順書](../../docs/operation/アプリケーション開発環境セットアップ手順書.md)、
設計は [docs/design](../../docs/design)、意思決定は [docs/adr](../../docs/adr) を参照。
