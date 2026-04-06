# アプリケーション開発環境セットアップ手順書

## 概要

本ドキュメントは、Case Study Cargo Tracker のアプリケーション開発環境をセットアップする手順を説明します。

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
| Java | 25.x | `java -version` |
| Gradle | 9.x（Wrapper で自動取得） | `./gradlew -v` |
| Docker | 24.x 以上 | `docker -v` |
| Docker Compose | 2.x | `docker compose version` |
| Git | 最新 | `git -v` |
| Node.js | 22.x LTS | `node -v` |
| npm | 10.x | `npm -v` |

### Java のインストール

SDKMAN を使用すると複数バージョンの管理が容易です。

```bash
# SDKMAN のインストール
curl -s "https://get.sdkman.io" | bash

# Java 25 のインストール
sdk install java 25-open

# バージョン確認
java -version
```

公式サイトから直接ダウンロードする場合：

- https://jdk.java.net/25/

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
| cargo-tracker | `apps/cargo-tracker` | 国際貨物輸送管理システム本体 | 5432 / 8080 |

---

## 4. 技術スタック

### バックエンド

| カテゴリ | 技術 | バージョン |
|---------|------|-----------|
| 言語 | Java | 25.x |
| フレームワーク | Spring Boot | 4.0.5 |
| Web | Spring MVC | 7.x |
| セキュリティ | Spring Security | 7.x |
| ビルドツール | Gradle | 9.x（Groovy DSL） |
| O/R マッパー | MyBatis | 4.0.1 (mybatis-spring-boot-starter) |
| API ドキュメント | springdoc-openapi (Swagger UI) | 3.0.2 |
| データベース（開発） | H2 | 2.x（インメモリ、PostgreSQL 互換モード） |
| データベース（本番） | PostgreSQL | 16.x |
| マイグレーション | Flyway | 10.x |
| テスト | JUnit 5 / Mockito 5 / AssertJ 3 | 5.x / 5.x / 3.x |
| 統合テスト | Testcontainers | 1.20.4 |
| Docker クライアント | docker-java | 3.7.0 |
| API E2E テスト | MockMvc + Testcontainers (PostgreSQL) | - |
| アーキテクチャテスト | ArchUnit | 1.4.1 |
| ブラウザ E2E テスト | Playwright | 1.44+ |
| 品質管理 | Checkstyle / SpotBugs / JaCoCo | - |

### フロントエンド

| カテゴリ | 技術 | バージョン |
|---------|------|-----------|
| テンプレートエンジン | Thymeleaf | 3.x |
| テンプレートセキュリティ | thymeleaf-extras-springsecurity6 | - |
| CSS フレームワーク | Bootstrap | 5.3.3 |
| 動的 UI | htmx | 2.0.4 |
| Webjars ロケーター | webjars-locator-lite | 1.0.1 |

### インフラストラクチャ

| カテゴリ | 技術 |
|---------|------|
| コンテナ | Docker / Docker Compose |
| CI/CD | GitHub Actions |
| タスクランナー | Gulp 5.x（Node.js） |

---

## 5. プロファイル構成

開発効率を高めるため、複数のプロファイルを使い分けます。

| プロファイル | データベース | Docker | 用途 |
|-------------|------------|--------|------|
| default | H2（インメモリ） | 不要 | 日常開発・即座起動 |
| product | PostgreSQL 16 | 必要 | 本番互換テスト |

### default プロファイル（推奨：日常開発）

Docker なしで即座に起動できます。

```bash
cd apps/cargo-tracker
./gradlew bootRun
```

### product プロファイル（本番互換）

```bash
# PostgreSQL コンテナを起動
docker compose up -d postgres

# product プロファイルで起動
cd apps/cargo-tracker
./gradlew bootRun --args='--spring.profiles.active=product'
```

---

## 6. 開発サーバーの起動

### タスクランナー経由（推奨）

```bash
# 開発サーバー起動（default プロファイル）
npx gulp dev

# TDD モード（テスト自動再実行）
npx gulp tdd

# タスク一覧を表示
npx gulp --tasks
```

### Gradle 直接実行

```bash
cd apps/cargo-tracker

# default プロファイルで起動
./gradlew bootRun

# TDD モード（テストを常に再実行）
./gradlew test --continuous
```

### アクセス確認

| サービス | URL | 説明 |
|---------|-----|------|
| アプリケーション | http://localhost:8080 | メインアプリケーション |
| Swagger UI | http://localhost:8080/swagger-ui/index.html | API ドキュメント |
| H2 コンソール | http://localhost:8080/h2-console | データベース管理（default プロファイル） |
| ヘルスチェック | http://localhost:8080/actuator/health | ヘルスチェック |
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
./gradlew test jacocoTestReport
```

### テストの種類

| テスト種別 | ツール | 説明 | 手順書 |
|-----------|--------|------|--------|
| 単体テスト | JUnit 5 + Mockito | ドメインロジック・ユースケースのテスト | - |
| 統合テスト | JUnit 5 + Testcontainers | PostgreSQL を使用した統合テスト | - |
| API E2E テスト | MockMvc + Testcontainers | REST API・Controller の結合フロー検証 | [手順書](./dev_e2e_api_instruction.md) |
| アーキテクチャテスト | ArchUnit | ヘキサゴナルアーキテクチャのレイヤー依存関係検証 | - |
| ブラウザ E2E テスト | Playwright | ブラウザ自動テスト（UI・JS 含む） | [手順書](./dev_e2e_instruction.md) |

```bash
# API E2E テストのみ実行
./gradlew test --tests "com.example.cargotracker.e2e.*"

# ブラウザ E2E テスト（アプリ起動中に別ターミナルで）
cd apps/cargo-tracker/e2e && npm test
```

### テストカバレッジ

```bash
# テストを実行してカバレッジレポートを生成
./gradlew test jacocoTestReport

# レポートの表示
# apps/cargo-tracker/build/reports/jacoco/test/html/index.html
```

---

## 9. コード品質管理

### 静的コード解析ツール

| ツール | 目的 | コマンド |
|--------|------|---------|
| Checkstyle | コーディング規約の検証 | `./gradlew checkstyleMain` |
| SpotBugs | バグパターン検出 | `./gradlew spotbugsMain` |
| JaCoCo | テストカバレッジ | `./gradlew jacocoTestReport` |

### 品質チェックの実行

```bash
cd apps/cargo-tracker

# 品質チェックのみ
./gradlew check

# すべてのテストと品質チェック
./gradlew build
```

### コード複雑度の基準

| 指標 | 閾値 | 説明 |
|------|------|------|
| 循環的複雑度 | 10 | 分岐・ループの複雑さ |
| ファイルサイズ | 500 行 | 1 ファイルの最大行数 |
| メソッドサイズ | 150 行 | 1 メソッドの最大行数 |
| パラメータ数 | 7 | メソッドの最大パラメータ数 |

### レポートの確認

品質チェック後、以下のディレクトリにレポートが生成されます。

| ツール | レポートパス |
|--------|-------------|
| Checkstyle | `apps/cargo-tracker/build/reports/checkstyle/` |
| SpotBugs | `apps/cargo-tracker/build/reports/spotbugs/` |
| JaCoCo | `apps/cargo-tracker/build/reports/jacoco/test/html/` |
| テスト結果 | `apps/cargo-tracker/build/reports/tests/test/` |

---

## 10. ディレクトリ構造

```
case-study-cargo-tracker/
├── .husky/                          # Git Hooks (Husky)
│   └── pre-commit                   # コミット前品質チェック
├── apps/
│   └── cargo-tracker/               # メインアプリケーション
│       ├── build.gradle             # Gradle ビルドスクリプト（Groovy DSL）
│       ├── settings.gradle          # Gradle 設定
│       ├── Dockerfile               # アプリコンテナイメージ
│       ├── config/                  # 品質管理ツール設定
│       │   ├── checkstyle/          # Checkstyle 設定
│       │   └── spotbugs/            # SpotBugs 除外フィルタ
│       └── src/
│           ├── main/
│           │   ├── java/com/example/cargotracker/
│           │   │   ├── shipper/                   # 荷主コンテキスト
│           │   │   │   ├── domain/model/
│           │   │   │   │   ├── aggregates/        # 集約ルート
│           │   │   │   │   ├── commands/          # コマンド
│           │   │   │   │   └── valueobjects/      # 値オブジェクト
│           │   │   │   ├── application/internal/
│           │   │   │   │   ├── commandservices/   # コマンドサービス
│           │   │   │   │   └── queryservices/     # クエリサービス
│           │   │   │   ├── infrastructure/
│           │   │   │   │   └── repositories/      # リポジトリ実装
│           │   │   │   └── interfaces/
│           │   │   │       ├── rest/              # REST Controller + dto/ + transform/
│           │   │   │       └── web/               # 画面 Controller
│           │   │   ├── booking/                   # 予約コンテキスト
│           │   │   │   ├── domain/model/
│           │   │   │   │   ├── aggregates/
│           │   │   │   │   ├── commands/
│           │   │   │   │   ├── entities/
│           │   │   │   │   └── valueobjects/
│           │   │   │   ├── application/internal/
│           │   │   │   │   ├── commandservices/
│           │   │   │   │   ├── queryservices/
│           │   │   │   │   └── outboundservices/acl/
│           │   │   │   ├── infrastructure/
│           │   │   │   │   ├── repositories/
│           │   │   │   │   └── services/
│           │   │   │   └── interfaces/
│           │   │   │       ├── rest/
│           │   │   │       └── web/
│           │   │   ├── shareddomain/              # 共有カーネル
│           │   │   │   ├── events/                # ドメインイベント
│           │   │   │   └── model/                 # 共有値オブジェクト（ShipperId 等）
│           │   │   └── shared/infrastructure/
│           │   │       ├── config/                # SecurityConfig, OpenApiConfig
│           │   │       └── web/                   # HomeController
│           │   └── resources/
│           │       ├── application.yml            # 開発設定（H2）
│           │       ├── application-product.yml    # 本番設定（PostgreSQL）
│           │       ├── db/migration/              # Flyway マイグレーション
│           │       ├── mapper/                    # MyBatis マッパー XML
│           │       └── templates/                 # Thymeleaf テンプレート
│           └── test/
│               ├── java/com/example/cargotracker/
│               │   ├── support/                   # テスト共通基盤
│               │   │   └── PostgreSQLIntegrationTestBase.java
│               │   └── e2e/                       # API E2E テスト
│               │       └── AuthE2ETest.java
│               └── resources/
│                   └── application-test.yml       # テスト用プロファイル
├── apps/
│   └── cargo-tracker/e2e/           # ブラウザ E2E テスト（Playwright）
│       ├── package.json
│       ├── playwright.config.ts
│       └── src/
│           ├── fixtures.ts
│           ├── pages/               # Page Object Model
│           └── tests/               # テストスペック
├── docs/                            # MkDocs ドキュメント
├── ops/                             # 運用スクリプト（Gulp タスク）
├── docker-compose.yml               # Docker サービス定義
└── package.json                     # Node.js 依存関係（Gulp, Husky）
```

---

## 11. 命名規則

| 要素 | 規則 | 例 |
|------|------|-----|
| テーブル名 | snake_case（複数形） | `shippers`, `bookings` |
| カラム名 | snake_case | `shipper_id`, `created_at` |
| クラス名 | PascalCase | `ShipperController`, `BookingId` |
| フィールド名 | camelCase | `shipperId`, `requestedPickupDate` |
| パッケージ名 | ドット区切り小文字 | `com.example.cargotracker.shipper.domain` |

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

サブシステムを示すスコープを使用します。

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

staged な Java ソースファイルがある場合、以下のチェックが自動実行されます。

| ツール | 目的 |
|--------|------|
| Checkstyle | コーディング規約の検証 |
| SpotBugs | バグパターン検出 |

実装上は `.husky/pre-commit` から `lint-staged` を起動し、`apps/cargo-tracker/src/**/*.java` が staged に含まれると `node ops/scripts/pre_commit_gradle_check.mjs` が `apps/cargo-tracker` で `./gradlew check` を実行します。いずれかのチェックが失敗すると、コミットがブロックされます。

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

# 2. ビルド確認
cd apps/cargo-tracker
./gradlew build -x test

# 3. テスト実行
./gradlew test

# 4. 品質チェック
./gradlew check

# 5. 開発サーバー起動（default プロファイル / H2 使用）
./gradlew bootRun
```

### アクセス確認

| サービス | URL | 説明 |
|---------|-----|------|
| アプリケーション | http://localhost:8080 | メインアプリケーション |
| Swagger UI | http://localhost:8080/swagger-ui/index.html | API ドキュメント |
| H2 コンソール | http://localhost:8080/h2-console | データベース管理ツール（dev のみ） |
| ヘルスチェック | http://localhost:8080/actuator/health | ヘルスチェック |
| MkDocs | http://localhost:8000 | プロジェクトドキュメント |

---

## 14. CI/CD

CI/CD による継続的インテグレーション・デプロイを設定しています。

### ワークフロー一覧

| ワークフロー | ファイル | トリガー | 説明 |
|-------------|----------|----------|------|
| Backend CI | `.github/workflows/backend-ci.yml` | `apps/cargo-tracker/**` 変更時 | 品質チェック・テスト・ビルド |
| Docker Publish | `.github/workflows/docker-publish.yml` | タグ push / 手動実行 | イメージを ECR に公開 |
| Docs Deploy | `.github/workflows/docs-deploy.yml` | main ブランチ push | MkDocs を GitHub Pages にデプロイ |

### Backend CI

バックエンドの変更時に自動実行されます。

```
実行内容:

  1. Java 25 環境セットアップ
  2. Gradle キャッシュ復元
  3. 品質チェック（Checkstyle、SpotBugs）
  4. テスト実行（単体・統合・アーキテクチャ）
  5. JaCoCo カバレッジレポート生成
  6. ビルド（JAR 生成）
  7. レポートアップロード（GitHub Actions Artifacts）
```

### Docker Image Publish

タグ push 時または手動実行時に、Docker イメージをビルドして ECR に公開します。

```bash
# タグによる自動実行
git tag v0.1.0
git push origin v0.1.0

# イメージの取得
docker pull {AWS_ACCOUNT_ID}.dkr.ecr.ap-northeast-1.amazonaws.com/cargo-tracker:latest
```

---

## トラブルシューティング

### Gradle Wrapper が見つからない

**問題**: `./gradlew` コマンドが実行できない

```
-bash: ./gradlew: No such file or directory
```

**解決策**: `apps/cargo-tracker` ディレクトリで実行しているか確認する

```bash
cd apps/cargo-tracker
./gradlew bootRun
```

### Testcontainers の起動に時間がかかる

**問題**: 統合テストが初回起動時に非常に遅い

**解決策**: Singleton コンテナパターンを使用して Docker コンテナを再利用する

```java
// 基底クラスで @Container をクラスレベルで定義
@Testcontainers
abstract class AbstractIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("cargo_tracker_test");
}
```

### H2 コンソールに接続できない

**問題**: http://localhost:8080/h2-console にアクセスできない

**解決策**: `application.yml` で H2 コンソールが有効になっているか確認する。Spring Boot 4.0 では `spring-boot-h2console` モジュールとして提供される。

```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

### pre-commit フックが失敗する場合

```bash
cd apps/cargo-tracker

# 品質チェックを手動実行してエラーを確認
./gradlew check

# エラーを修正してから再度コミット
```

---

## 関連ドキュメント

- [技術スタック選定](../design/tech_stack.md)
- [バックエンドアーキテクチャ設計](../design/architecture_backend.md)
- [Playwright E2E テストセットアップ手順書](./dev_e2e_instruction.md)
- [API E2E テストセットアップ手順書](./dev_e2e_api_instruction.md)
- [IT1 計画（US02・US03・US04）](../development/iteration_plan-1.md)
- [開発環境セットアップ手順書](./dev_infa_instruction.md)
