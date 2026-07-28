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

## 主な業務フロー（IT2 時点）

営業担当者（`sales`）でログインすると以下を実行できる。

1. **見積作成**: `/estimates/new` → 出発地・目的地（UN/LOCODE）・期限・重量・貨物種別を入力 → ルート候補（スタブ算出）と見積番号が発行される
2. **荷主登録**: `/shippers/new` → 発行された荷主 ID（`SHP-xxxxxxxx`）を控える
3. **貨物予約**: `/bookings/new` → 荷主 ID・荷受人・貨物仕様を入力 → 仮受付（予約番号発行）
4. **引き渡し**: 予約詳細（`/bookings/{id}`）→「経路設計者に引き渡す」→ 経路設計中（`/bookings?status=ROUTING_IN_PROGRESS` で経路設計者が確認）

> **前提**: 貨物予約には荷主が必要。`seed.ts` は荷主をシードしないため、先に **2. 荷主登録** で荷主 ID を発行すること（未登録だと「該当する荷主が見つかりません」エラー）。
>
> **UN/LOCODE**: 出発地・目的地はシード済みコードを入力する。フォームの候補（datalist）から選択できる:
> `JPTYO`(東京) `JPOSA`(大阪) `JPYOK`(横浜) `USLAX`(LA) `USNYC`(NY) `SGSIN`(シンガポール) `CNSHA`(上海) `NLRTM`(ロッテルダム) `DEHAM`(ハンブルク) `HKHKG`(香港)

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
