# Cargo Tracker（Scala 版）

国際貨物輸送管理システム — Phase 1（IT1+IT2、Release 0.1 Internal Alpha）まで実装済み。

## クイックスタート

### 前提条件

| 項目 | バージョン |
|------|----------|
| JDK | 25（Temurin 推奨） |
| sbt | 1.10.7 以上 |
| Docker Desktop | 起動済み（PostgreSQL コンテナ用） |
| Node.js | 22.x LTS（E2E テスト用） |

### 1. PostgreSQL 起動

```bash
cd apps/cargo-tracker
docker compose up -d postgres
```

### 2. アプリケーション起動

```bash
# プロジェクトルートで
npm start
# または apps/cargo-tracker で直接
cd apps/cargo-tracker
sbt "run 9001"
```

ポート 9001 で起動します（9000 は SonarQube が占有するため）。`/health` で疎通確認:

```bash
curl http://localhost:9001/health
# {"status":"UP"}
```

### 3. ログイン

ブラウザで [http://localhost:9001/login](http://localhost:9001/login) を開き、シードユーザーでログイン:

| 項目 | 値 |
|------|-----|
| ユーザー ID | `admin` |
| パスワード | `Adm1nPass!` |

> **本番運用**: 資格情報は `application.conf` の `cargotracker.admin-seed` で外出し済み。環境変数 `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` で上書き可能。本番では `ADMIN_SEED_ENABLED=false` に設定し Secrets Manager 等から差し替える。

### 4. ロール別ダッシュボード

`admin` は MasterAdmin ロール（全業務操作可）で、ダッシュボードに以下のカードが表示されます:

| ロール | 表示カード |
|--------|----------|
| Sales | 見積管理 / 荷主管理 / 貨物予約 |
| RouteDesigner | 経路設計依頼（引き渡し済み予約一覧）/ 航路管理 |
| Tracker | 追跡・荷役（IT3+） |
| Settlement | 精算（IT4+） |
| MasterAdmin | 上記すべて + マスタ管理 |

ナビバーもロールに応じて表示制御されます。

## 実装済みユーザーストーリー

### Release 0.1 Internal Alpha（IT1+IT2、24 SP）

| US | 機能 | URL |
|----|------|-----|
| US26 | 認証（ログイン / ログアウト / 30 分タイムアウト） | `/login`, `/logout` |
| US02 | 個人荷主登録 | `/shippers/new` |
| US03 | 法人荷主登録（契約番号・割引率付き） | `/shippers/new` |
| US01 | 輸送見積作成（ルート候補生成） | `/estimates/new` |
| US04 | 貨物予約登録 | `/bookings/new` |
| US05 | 危険物・冷凍貨物予約（条件付き必須） | `/bookings/new` |
| US06 | 経路設計者への引き渡し（Preliminary → RouteProposed） | `/bookings/:id` |
| US24 | 航海スケジュール新規登録 | `/voyages/new` |
| US25 | 航海スケジュール更新（差分確認） | `/voyages/:voyageNumber/edit` |

詳細は [CHANGELOG](./CHANGELOG.md) を参照。

## 開発

### よく使うコマンド

```bash
# ユニット / 統合 / Arch テスト
sbt test

# E2E テスト（要: アプリ起動済み）
cd e2e
npm install
npx playwright install chromium
npm test

# カバレッジ計測
sbt clean coverage test coverageReport

# Lint（pre-commit hook と同等）
sbt scalafmtCheckAll
sbt "scalafixAll --check"  # CI でのみ実行（ローカル pre-commit は scalafmt のみ）

# SonarQube スキャン
sbt sonarScan
```

### 設計可視化（JIG）

[dddjava/jig](https://github.com/dddjava/jig) で Scala バイトコードを解析し、パッケージ関連図・ドメインモデル図・ユースケース図などを HTML 生成する。

```bash
# 前提: graphviz（dot）が必要
brew install graphviz

# リポジトリルートで実行（初回は jig-cli.jar を自動ダウンロード）
npx gulp jig:report        # sbt compile + JIG ドキュメント生成
npx gulp jig:report:only   # コンパイル済み前提で JIG のみ
npx gulp jig:open          # build/jig/index.html をブラウザで開く
```

出力先は `apps/cargo-tracker/build/jig/`（gitignore 対象）。設定は `jig.properties`（JIG 固有 `jig.*`）と `application.properties`（Spring 側 `directory.*` / `project.path`）に分かれる。JIG のバージョンは環境変数 `JIG_VERSION` で切り替え可能。

### アーキテクチャ

ヘキサゴナル DDD + Scala 3 イミュータブル設計（[architecture_backend.md](../../docs/design/architecture_backend.md) 参照）。各境界付けられたコンテキスト（auth / booking / estimation / routing / shipper / shared）は以下のレイヤーで構成:

```
<context>/
├── domain/model/{aggregates,valueobjects,repositories,acl,commands,entities,events}/
├── application/{commandservices,queryservices,outboundservices/acl}/
├── infrastructure/{repositories,services}/
└── interfaces/web/
```

依存方向は `interfaces → application → domain` 単方向、infrastructure は domain の port（trait）を実装。ArchUnit 5 ルールで自動検証（[HexagonalArchitectureSpec.scala](./test/cargotracker/arch/HexagonalArchitectureSpec.scala)）。

### テスト

| 種別 | 件数 | 場所 |
|------|------|------|
| ユニット（ドメイン） | 多数 | `test/cargotracker/<context>/domain/model/` |
| 統合（Testcontainers PostgreSQL） | 多数 | `test/cargotracker/<context>/infrastructure/` |
| ArchUnit | 5 | `test/cargotracker/arch/HexagonalArchitectureSpec.scala` |
| E2E（Playwright） | 14 | `e2e/src/tests/` |

`DbCleanupSupport` trait で統合テスト間の独立性を担保。

## ドキュメント

- [リリース計画](../../docs/development/release_plan.md)
- [イテレーション計画](../../docs/development/)
- [アーキテクチャ設計](../../docs/design/architecture_backend.md)
- [ドメインモデル設計](../../docs/design/domain-model.md)
- [データモデル設計](../../docs/design/data-model.md)
- [UI 設計](../../docs/design/ui_design.md)
- [テスト戦略](../../docs/design/test_strategy.md)
- [ADR 一覧](../../docs/adr/index.md)
- [CHANGELOG](./CHANGELOG.md)
