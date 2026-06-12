# アプリケーション開発環境セットアップ手順書

## 概要

本ドキュメントは、Case Study Cargo Tracker（Scala 版）のアプリケーション開発環境をセットアップする手順を説明します。

テスト駆動開発（TDD）のゴールは **動作するきれいなコード** です。それを実現するためには [ソフトウェア開発の三種の神器](https://t-wada.hatenablog.jp/entry/clean-code-that-works) が必要です。

> 今日のソフトウェア開発の世界において絶対になければならない 3 つの技術的な柱があります。
> 三本柱と言ったり、三種の神器と言ったりしていますが、それらは
>
> - バージョン管理
> - テスティング
> - 自動化
>
> の 3 つです。
>
> — https://t-wada.hatenablog.jp/entry/clean-code-that-works

---

## 1. 前提条件

以下のツールがインストールされていることを確認してください。

| ツール | バージョン | 確認コマンド |
|--------|-----------|-------------|
| JDK（Temurin） | 21.x LTS | `java -version` |
| sbt | 1.10.x | `sbt --version` |
| Docker | 24.x 以上 | `docker -v` |
| Docker Compose | 2.x | `docker compose version` |
| Git | 最新 | `git -v` |
| Node.js | 22.x LTS | `node -v` |
| npm | 10.x | `npm -v` |

> Scala 3.3.x（LTS）は sbt がプロジェクト定義（`build.sbt`）に従って自動取得するため、個別のインストールは不要です。

### JDK と sbt のインストール

SDKMAN を使用すると複数バージョンの管理が容易です。

```bash
# SDKMAN のインストール
curl -s "https://get.sdkman.io" | bash

# JDK 21（Temurin）のインストール
sdk install java 21-tem

# sbt のインストール
sdk install sbt

# バージョン確認
java -version
sbt --version
```

公式サイトから直接ダウンロードする場合：

- https://adoptium.net/temurin/releases/
- https://www.scala-sbt.org/download/

### Docker のインストール

Docker Desktop をインストールします。

- **Windows**: https://docs.docker.com/desktop/install/windows-install/
- **macOS**: https://docs.docker.com/desktop/install/mac-install/

```bash
# バージョン確認
docker -v

docker compose version
```

### Node.js のインストール

コミット前の品質チェック（husky + lint-staged）と Gulp タスクランナーに Node.js が必要です。

- https://nodejs.org/

```bash
# バージョン確認
node -v

npm -v
```

### IDE

| IDE | プラグイン | 備考 |
|-----|-----------|------|
| IntelliJ IDEA | Scala プラグイン | 推奨。Play ルート・Twirl テンプレートの補完に対応 |
| VS Code | Metals | LSP ベース。軽量な代替 |

---

## 2. プロジェクトの取得

### リポジトリのクローン

```bash
git clone https://github.com/k2works/case-study-cargo-tracker.git
cd case-study-cargo-tracker
```

### Node.js 依存パッケージのインストール

```bash
npm install
```

> **Note**: husky（Git Hooks）が `prepare` スクリプトで自動的にセットアップされます。

---

## 3. サブシステム一覧

Case Study Cargo Tracker は以下のサブシステムで構成されています。

| システム | ディレクトリ | 説明 | ポート (DB / App) |
|---------|-------------|------|-------------------|
| cargo-tracker | `apps/cargo-tracker` | 国際貨物輸送管理システム本体 | 5432 / 9000 |

---

## 4. 技術スタック

詳細は [技術スタック選定](../design/tech_stack.md) を参照してください。

### バックエンド

| カテゴリ | 技術 | バージョン |
|---------|------|-----------|
| 言語 | Scala | 3.3.x LTS |
| ランタイム | JDK（Temurin） | 21 LTS |
| フレームワーク | Play Framework | 3.0.x（Pekko ベース） |
| DI | Guice | 6.x（Play 標準） |
| ビルドツール | sbt | 1.10.x |
| DB アクセス | ScalikeJDBC | 4.3.x |
| 接続プール | HikariCP | - |
| データベース | PostgreSQL | 16.x |
| マイグレーション | Flyway（flyway-play） | - |
| パスワードハッシュ | jbcrypt | 0.4 |
| テスト | ScalaTest / ScalaTestPlus-Play | - |
| 統合テスト | testcontainers-scala（PostgreSQL） | - |
| 外部 API スタブ | WireMock | - |
| アーキテクチャテスト | ArchUnit | - |
| ブラウザ E2E テスト | Playwright | 1.44+ |
| 品質管理 | scalafmt / scalafix / scoverage | - |

> **重要**: 本プロジェクトは H2 等のインメモリ DB を使用しません。ローカル開発・テストとも実 PostgreSQL（Docker / Testcontainers）を使用し、本番との差異を排除します（[テスト戦略](../design/test_strategy.md)）。

### フロントエンド

| カテゴリ | 技術 | バージョン |
|---------|------|-----------|
| テンプレートエンジン | Twirl | 2.0.x（Play 同梱） |
| CSS フレームワーク | Bootstrap | 5.3.x |
| 動的 UI | htmx | 2.0.x |

### インフラストラクチャ

| カテゴリ | 技術 |
|---------|------|
| コンテナ | Docker / Docker Compose |
| CI/CD | GitHub Actions |
| タスクランナー | Gulp 5.x（Node.js） |

---

## 5. 設定ファイル構成

環境切替は Typesafe Config の設定ファイルオーバーレイで行います（Spring Profile 相当）。

| 設定ファイル | データベース | Docker | 用途 |
|-------------|------------|--------|------|
| `application.conf`（デフォルト） | PostgreSQL（Docker） | 必要 | 日常開発 |
| `staging.conf` / `production.conf` | RDS PostgreSQL | - | AWS 環境（`-Dconfig.resource` で指定） |
| テスト時（Testcontainers） | PostgreSQL（自動起動） | 必要 | ユニット・統合テスト |

### ローカル開発（デフォルト）

```bash
# PostgreSQL コンテナを起動
docker compose up -d postgres

# Play 開発モードで起動（ホットリロード有効）
cd apps/cargo-tracker
sbt run
```

> Play の開発モード（`sbt run`）はソース変更を検知して自動リコンパイルします。Flyway マイグレーション（flyway-play）は起動時に自動適用されます。

---

## 6. 開発サーバーの起動

### タスクランナー経由（推奨）

```bash
# 開発サーバー起動（PostgreSQL 起動込み）
npx gulp dev

# TDD モード（テスト自動再実行）
npx gulp tdd

# タスク一覧を表示
npx gulp --tasks
```

### sbt 直接実行

```bash
cd apps/cargo-tracker

# 開発モードで起動（http://localhost:9000）
sbt run

# TDD モード（ソース変更を検知してテストを再実行）
sbt ~test
```

### アクセス確認

| サービス | URL | 説明 |
|---------|-----|------|
| アプリケーション | http://localhost:9000 | メインアプリケーション |
| ヘルスチェック | http://localhost:9000/health | ヘルスチェック（DB 疎通込み） |
| Adminer | http://localhost:8081 | データベース管理 UI |
| MailHog | http://localhost:8025 | メール送信確認 |
| MkDocs | http://localhost:8000 | プロジェクトドキュメント |

---

## 7. Docker Compose のセットアップ

### データベースコンテナの起動

```bash
# PostgreSQL を起動
docker compose up -d postgres

# コンテナの状態確認
docker compose ps
```

### Docker Compose の便利なコマンド

```bash
# PostgreSQL を起動
docker compose up -d postgres

# コンテナの停止と削除
docker compose down

# ログを確認
docker compose logs -f postgres

# PostgreSQL に接続
docker compose exec postgres psql -U cargo_tracker -d cargo_tracker
```

### MkDocs ドキュメントサーバーの起動

```bash
# MkDocs サーバー起動（ポート 8000）
docker compose up -d mkdocs

# ブラウザでアクセス
# http://localhost:8000
```

---

## 8. テストの実行

### 全テスト実行

```bash
cd apps/cargo-tracker

# テスト実行（カバレッジレポート付き）
sbt clean coverage test coverageReport
```

> **Note**: 統合テスト（testcontainers-scala）は Docker を使用して PostgreSQL コンテナを自動起動します。Docker Desktop が起動している必要があります。

### テストの種類

| テスト種別 | ツール | 説明 | 手順書 |
|-----------|--------|------|--------|
| ユニットテスト | ScalaTest | ドメインロジック・アプリケーションサービスのテスト | - |
| 統合テスト | ScalaTest + testcontainers-scala | 実 PostgreSQL を使用したリポジトリテスト | - |
| コントローラー E2E テスト | ScalaTestPlus-Play + Testcontainers | 画面 Controller・認証・Form を結合したフロー検証 | [手順書](./dev_e2e_api_instruction.md) |
| アーキテクチャテスト | ArchUnit | ヘキサゴナルアーキテクチャのレイヤー依存関係検証（4 ルール） |  - |
| 契約テスト | WireMock | 外部 5 システム ACL ポートの正常・異常応答検証 | - |
| ブラウザ E2E テスト | Playwright | ブラウザ自動テスト（htmx・UI 含む） | [手順書](./dev_e2e_instruction.md) |

```bash
# 特定のテストクラスのみ実行
sbt "testOnly cargotracker.booking.domain.model.CargoSpec"

# パッケージ単位で実行（コントローラー E2E のみ等）
sbt "testOnly cargotracker.e2e.*"

# ブラウザ E2E テスト（アプリ起動中に別ターミナルで）
cd apps/cargo-tracker/e2e && npm test
```

### テストカバレッジ

```bash
# テストを実行してカバレッジレポートを生成
sbt clean coverage test coverageReport

# レポートの表示
# apps/cargo-tracker/target/scala-3.3.*/scoverage-report/index.html
```

カバレッジ目標（[テスト戦略](../design/test_strategy.md)）: ドメイン層 85% / アプリケーション層 80% / インフラ層 75% / インターフェース層 70%、全体 80%（`coverageFailOnMinimum := true`）。

---

## 9. コード品質管理

### 静的コード解析ツール

| ツール | 目的 | コマンド |
|--------|------|---------|
| scalafmt | コードフォーマットの検証 | `sbt scalafmtCheckAll` |
| scalafix | 構文・セマンティックルールの検証 | `sbt "scalafixAll --check"` |
| scoverage | テストカバレッジ | `sbt coverage test coverageReport` |
| Scala 3 コンパイラ | 警告ゼロ（`-Werror`） | `sbt compile` |

### 品質チェックの実行

```bash
cd apps/cargo-tracker

# フォーマット適用
sbt scalafmtAll

# 品質チェックのみ（CI と同一）
sbt scalafmtCheckAll "scalafixAll --check"

# すべてのテストと品質チェック
sbt scalafmtCheckAll "scalafixAll --check" clean coverage test coverageReport
```

### レポートの確認

| ツール | レポートパス |
|--------|-------------|
| scoverage | `apps/cargo-tracker/target/scala-3.3.*/scoverage-report/` |
| テスト結果 | `apps/cargo-tracker/target/test-reports/` |

> SonarQube は可視化（非ブロッキング）として運用します。マージをブロックする品質ゲートは scalafmt / scalafix / scoverage / `-Werror` です（[非機能要件定義](../design/non_functional.md)）。

---

## 10. ディレクトリ構造

[バックエンドアーキテクチャ](../design/architecture_backend.md) のパッケージ構造に従います。

```
case-study-cargo-tracker/
├── .husky/                          # Git Hooks (Husky)
│   └── pre-commit                   # コミット前品質チェック
├── apps/
│   └── cargo-tracker/               # メインアプリケーション
│       ├── build.sbt                # sbt ビルド定義
│       ├── project/                 # sbt プラグイン（scalafmt, scalafix, scoverage, native-packager）
│       ├── Dockerfile               # アプリコンテナイメージ（sbt stage → temurin-21-jre）
│       ├── .scalafmt.conf           # scalafmt 設定
│       ├── .scalafix.conf           # scalafix 設定
│       ├── app/
│       │   ├── cargotracker/
│       │   │   ├── booking/                       # 予約コンテキスト
│       │   │   │   ├── domain/model/              # 集約・値オブジェクト・enum（Play 非依存）
│       │   │   │   ├── application/
│       │   │   │   │   ├── commandservices/       # コマンドサービス（DB.localTx）
│       │   │   │   │   ├── queryservices/         # クエリサービス（CQRS 読み取り）
│       │   │   │   │   └── outboundservices/acl/  # ACL
│       │   │   │   ├── infrastructure/
│       │   │   │   │   └── repositories/          # ScalikeJDBC リポジトリ実装
│       │   │   │   └── interfaces/
│       │   │   │       ├── web/                   # 画面 Controller + Play Form
│       │   │   │       ├── rest/                  # JSON API Controller
│       │   │   │       └── events/                # DomainEventSubscriber
│       │   │   ├── shipper/                       # 荷主コンテキスト（同構造）
│       │   │   ├── routing/                       # 経路コンテキスト
│       │   │   ├── tracking/                      # 追跡コンテキスト
│       │   │   ├── handling/                      # 荷役コンテキスト
│       │   │   ├── billing/                       # 請求コンテキスト
│       │   │   ├── estimation/                    # 見積コンテキスト
│       │   │   └── shared/                        # 共有カーネル（Location, DomainEvent 等）
│       │   ├── views/                             # Twirl テンプレート（layout/ fragments/ 各画面）
│       │   └── Module.scala                       # Guice バインディング
│       ├── conf/
│       │   ├── routes                             # ルーティング定義
│       │   ├── application.conf                   # 共通設定
│       │   └── db/migration/default/              # Flyway マイグレーション
│       ├── test/                                  # ユニット・統合・E2E テスト
│       │   └── cargotracker/
│       │       ├── support/                       # Testcontainers 共通基盤
│       │       └── e2e/                           # コントローラー E2E テスト
│       └── e2e/                                   # ブラウザ E2E テスト（Playwright）
│           ├── package.json
│           ├── playwright.config.ts
│           └── src/
├── docs/                            # MkDocs ドキュメント
├── ops/                             # 運用スクリプト（Gulp タスク）・Terraform
├── docker-compose.yml               # Docker サービス定義
└── package.json                     # Node.js 依存関係（Gulp, Husky）
```

---

## 11. 命名規則

| 要素 | 規則 | 例 |
|------|------|-----|
| テーブル名 | snake_case（単数形） | `cargo`, `shipper`, `tracking_activity` |
| カラム名 | snake_case | `booking_id`, `created_at` |
| クラス名 | PascalCase | `BookingWebController`, `BookingId` |
| フィールド名 | camelCase | `bookingId`, `arrivalDeadline` |
| パッケージ名 | ドット区切り小文字 | `cargotracker.booking.domain.model` |
| enum の DB 永続化値 | SCREAMING_SNAKE_CASE | `ROUTE_PROPOSED`, `ONBOARD_CARRIER` |

---

## 12. Git 規約

### コミットメッセージ

[Conventional Commits](https://www.conventionalcommits.org/ja/) に従います。

| タイプ | 説明 |
|--------|------|
| `feat` | 新機能 |
| `fix` | バグ修正 |
| `docs` | ドキュメントのみの変更 |
| `style` | コードの意味に影響しない変更 |
| `refactor` | バグ修正でも機能追加でもないコード変更 |
| `perf` | パフォーマンス改善 |
| `test` | テストの追加・修正 |
| `chore` | ビルドプロセス・補助ツールの変更 |

### スコープ

境界付けられたコンテキストを示すスコープを使用します。

```
feat(shipper): 荷主登録機能を実装する
feat(booking): 貨物予約登録機能を実装する
fix(booking): 重量バリデーションのバグを修正する
docs: IT1 計画を更新する
```

### Git Hooks（Husky + lint-staged）

コミット時に自動で品質チェックが実行されます。

#### セットアップ

`npm install` 実行時に Husky は自動的にセットアップされます（`prepare` スクリプト）。

```bash
# 手動でセットアップする場合
npm run prepare
```

#### pre-commit フック

staged な Scala ソースファイル（`apps/cargo-tracker/app/**/*.scala`、`test/**/*.scala`）がある場合、以下のチェックが自動実行されます。

| ツール | 目的 |
|--------|------|
| scalafmt | コードフォーマットの検証（`scalafmtCheckAll`） |
| scalafix | 構文・セマンティックルールの検証（`scalafixAll --check`） |

いずれかのチェックが失敗すると、コミットがブロックされます。

#### フックをスキップする場合

緊急時にフックをスキップしてコミットする場合（非推奨）：

```bash
git commit --no-verify -m "メッセージ"
```

> **Warning**: フックのスキップは緊急時のみ使用してください。品質チェックを通過しないコードはチームに影響を与える可能性があります。

---

## 13. セットアップの確認

すべてのセットアップが完了したら、以下のコマンドで動作確認を行います。

```bash
# 1. Node.js 依存パッケージのインストール
npm install

# 2. PostgreSQL の起動
docker compose up -d postgres

# 3. ビルド確認
cd apps/cargo-tracker
sbt compile

# 4. テスト実行
sbt test

# 5. 品質チェック
sbt scalafmtCheckAll "scalafixAll --check"

# 6. 開発サーバー起動
sbt run
```

### アクセス確認

| サービス | URL | 説明 |
|---------|-----|------|
| アプリケーション | http://localhost:9000 | メインアプリケーション |
| ヘルスチェック | http://localhost:9000/health | ヘルスチェック（`{"status":"UP"}`） |
| Adminer | http://localhost:8081 | データベース管理 UI |
| MkDocs | http://localhost:8000 | プロジェクトドキュメント |

---

## 14. CI/CD

CI/CD による継続的インテグレーション・デプロイを設定しています（[インフラストラクチャアーキテクチャ](../design/architecture_infrastructure.md)）。

### ワークフロー一覧

| ワークフロー | ファイル | トリガー | 説明 |
|-------------|----------|----------|------|
| Backend CI | `.github/workflows/ci.yml` | `apps/cargo-tracker/**` 変更時 | 品質チェック・テスト・ビルド |
| CD Staging | `.github/workflows/cd-staging.yml` | main ブランチ push | ステージング環境への自動デプロイ |
| CD Production | `.github/workflows/cd-production.yml` | 手動承認 / release タグ | 本番環境へのデプロイ |
| Docs Deploy | `.github/workflows/docs-deploy.yml` | main ブランチ push | MkDocs を GitHub Pages にデプロイ |

### Backend CI

バックエンドの変更時に自動実行されます。

```
実行内容:

  1. JDK 21 + sbt 環境セットアップ（Coursier キャッシュ復元）
  2. 品質チェック（scalafmtCheckAll / scalafixAll --check）
  3. ユニットテスト（ScalaTest）
  4. 統合テスト（Testcontainers / ScalaTestPlus-Play）
  5. scoverage カバレッジレポート生成
  6. ビルド（sbt stage）
  7. レポートアップロード（GitHub Actions Artifacts）
```

### Docker Image Publish

タグ push 時または手動実行時に、Docker イメージ（マルチステージビルド: `sbt stage` → `eclipse-temurin:21-jre-alpine`）をビルドして ECR に公開します。

```bash
# タグによる自動実行
git tag v0.1.0
git push origin v0.1.0

# イメージの取得
docker pull {AWS_ACCOUNT_ID}.dkr.ecr.ap-northeast-1.amazonaws.com/cargo-tracker:latest
```

---

## トラブルシューティング

### sbt の初回起動が遅い

**問題**: `sbt run` の初回実行に数分かかる

**解決策**: sbt は初回に依存ライブラリ（Coursier）と Scala コンパイラを取得します。2 回目以降はキャッシュされ高速化されます。CI でも Coursier キャッシュを復元しています。

### PostgreSQL に接続できない

**問題**: 起動時に `Connection refused` で Flyway マイグレーションが失敗する

**解決策**: PostgreSQL コンテナが起動しているか確認する

```bash
docker compose ps
docker compose up -d postgres
```

### Testcontainers の起動に時間がかかる

**問題**: 統合テストが初回起動時に非常に遅い

**解決策**: `TestContainerForAll`（testcontainers-scala）でスイート間のコンテナ共有を確認する

```scala
// 共通 trait でコンテナをスイート全体に共有
trait PostgresContainerSupport extends TestContainerForAll { self: Suite =>
  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:16-alpine"))
}
```

### Twirl テンプレートの変更が反映されない

**問題**: `app/views/` の変更が画面に反映されない

**解決策**: Twirl は Scala コードにコンパイルされます。`sbt run`（開発モード）ではリクエスト時に自動リコンパイルされるため、ブラウザをリロードしてください。エラーが出る場合はコンパイルエラーの内容を確認します。

### `-Werror` で警告がエラーになる

**問題**: 軽微な警告でコンパイルが失敗する

**解決策**: 本プロジェクトは警告ゼロを品質ゲートとしています。警告を修正してください。やむを得ず抑制する場合は `@nowarn` に理由を添えて限定的に使用します。

### pre-commit フックが失敗する場合

```bash
cd apps/cargo-tracker

# フォーマットを適用してから品質チェックを再実行
sbt scalafmtAll
sbt scalafmtCheckAll "scalafixAll --check"

# エラーを修正してから再度コミット
```

---

## 関連ドキュメント

- [技術スタック選定](../design/tech_stack.md)
- [バックエンドアーキテクチャ設計](../design/architecture_backend.md)
- [テスト戦略](../design/test_strategy.md)
- [Playwright E2E テストセットアップ手順書](./dev_e2e_instruction.md)
- [コントローラー E2E テストセットアップ手順書](./dev_e2e_api_instruction.md)
- [開発環境セットアップ手順書](./dev_infra_instruction.md)
