# Cargo Tracker (Haskell 版)

国際貨物輸送管理システムの Haskell 版アプリケーション本体。

## クイックスタート

```bash
cd apps/cargo-tracker

# 1. 環境変数を設定
cp .env.example .env

# 2. PostgreSQL を起動
docker compose up -d

# 3. マイグレーション適用 (dbmate)
dbmate --url "$DATABASE_URL" --migrations-dir db/migrations up

# 4. 依存解決 + ビルド (初回 10-30 分)
stack setup
stack build

# 5. テスト実行
stack test

# 6. 起動
stack run cargo-tracker-exe
```

ヘルスチェック確認:

```bash
curl http://localhost:8080/health
# => {"status":"UP","message":"Hello, world! Cargo Tracker (Haskell) is alive."}
```

## アーキテクチャ

DDD + ヘキサゴナル + CQRS。詳細は以下を参照:

- [バックエンドアーキテクチャ](../../docs/design/architecture_backend.md)
- [ドメインモデル設計](../../docs/design/domain-model.md)
- [ADR 0001 Haskell + Servant スタック](../../docs/adr/0001-haskell-servant-stack.md)
- [ADR 0002 arch-check 実装](../../docs/adr/0002-arch-check-implementation.md)

## 開発手順

詳細は [アプリケーション開発環境セットアップ手順書](../../docs/operation/アプリケーション開発環境セットアップ手順書.md) を参照。

### 主要コマンド

```bash
# フォーマット適用
fourmolu --mode inplace src/ test/ app/

# HLint
hlint src/ test/

# テスト + カバレッジ
stack test --coverage
stack hpc report --all

# ホットリロード開発 (ghcid)
ghcid --command="stack ghci" --test=":main"

# 全クリーンビルド
stack clean && stack build --pedantic
```

## ディレクトリ構造

```text
apps/cargo-tracker/
├── package.yaml         # hpack パッケージ仕様
├── stack.yaml           # Stack resolver
├── Dockerfile           # マルチステージ (Haskell → debian-slim)
├── docker-compose.yml   # PostgreSQL + Adminer + MailHog
├── .env.example         # 環境変数サンプル
├── .hlint.yaml          # HLint カスタムルール (ADR 0002)
├── fourmolu.yaml        # フォーマッタ設定
├── arch-check.yaml      # arch-check ルール定義 (ADR 0002)
├── app/Main.hs          # Warp 起動 (IT1 で本実装)
├── src/Cargotracker/    # ドメインコード (Bounded Context 別)
│   └── Shared/Domain/   # 共有カーネル
├── test/                # hspec + hedgehog テスト
├── db/migrations/       # dbmate SQL マイグレーション
└── static/              # Bootstrap / htmx / 画像
```

## IT1 着手前のスタブ

現時点 (Sprint 0 完了直後) のコードは **IT1 で本格実装するためのスタブ** です。

- `Main.hs`: Warp に最小 JSON レスポンスを返すだけ
- `Cargotracker.hs`: `greet` 関数のみ
- `Cargotracker.Shared.Domain.DomainError`: `InvalidBookingId` / `InvalidUnLocode` / `ConcurrentModification` のみ
- テスト: スタブの動作確認だけ

IT1 のスコープは [リリース計画](../../docs/development/release_plan.md) §イテレーション 1 を参照。
