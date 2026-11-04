---
type: Design
title: "技術スタック一覧：国際貨物輸送管理システム"
description: "Scala 3 LTS・Pekko・PostgreSQL・React・AWS を軸にした技術スタックの提案。バージョン・サポート期限・ADR 候補 TS-01..12 を整理した草案"
tags: [design]
status: draft
generated: { by: claude-code/claude-sonnet-5-5, at: 2026-11-04T02:47:01Z }
---

# 技術スタック一覧：国際貨物輸送管理システム

[バックエンド](architecture_backend.md)・[フロントエンド](architecture_frontend.md)・[インフラストラクチャ](architecture_infrastructure.md)のアーキテクチャと、[データモデル](data-model.md)に合わせて、技術を選定します。バックエンドのビルドは Scala と sbt、フロントエンドは TypeScript と npm、インフラは AWS と Terraform を基本にします。

- **本書は提案です**。外部ライブラリの導入とデプロイ設定は確認必須の操作で、人が承認してから導入します（R-01、R-08、リスク台帳の確認必須の操作）。承認前に依存を追加しません
- 確認日は **2026-11-04** です。バージョンとサポート期限は、確認できたものだけに数値を書き、確認できなかったものは「着手時に確認」とします。推測の数値は置きません
- 参照実装（`tmp/pekko-cqrs-es-example`）のバージョンは**古いものが多い**ため、そのまま写しません（Scala 3.6.2、Pekko 1.1.3、logback 1.3.14、slf4j 1.7.36、ScalaTest 3.2.12、ZIO 2.0.21 など）

---

## 1. 読み方

| 記号 | 意味 |
| :--- | :--- |
| ✓ | 公開情報で確認した（出典は[第 9 章](#9-出典)） |
| △ | 参照実装またはリポジトリの現状に由来する。最新の安定版かは未確認 |
| ? | 確認できなかった。**着手時に最新の安定版を確認して固定する** |
| 条件付き | 他の判断（BE-03 など）の結果で採否が決まる |

サポート期限は「ベンダーまたはプロジェクトが公表した、セキュリティ修正を受けられる期限」です。期限が確認できない技術は「—」とし、確認の方法を備考に書きます。

---

## 2. バックエンド

### 2.1 言語・実行環境・ビルド

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 確度・備考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 言語 | Scala 3（**LTS**） | 3.9.x | ドメイン・ユースケース・アダプタ | 3.9 系：**2029-09 以降**（3.3 系は 2027-09 まで） | ✓ 3.9 LTS は JDK 17 以上が必須。最新のパッチは着手時に確認して固定。参照実装の 3.6.2 は LTS でない |
| JDK | Eclipse Temurin | **21（LTS）** | 実行・ビルド | 2029-12-31 | ✓ 参照実装も 21 を推奨。25（LTS、**2031-09-30**）への移行は、依存ライブラリの対応を確かめてから（[第 7 章](#7-アップグレード計画)） |
| ビルド | sbt | 1.12.x | マルチモジュールのビルド | — | ✓ 1.12 系が最新。参照実装は 1.10 系、記事コード（`docs/article/source/scala`）は 1.11.6 |
| 開発環境 | Nix シェル（`ops/nix/environments/scala`） | `scala_3`・`sbt`・`metals`・`scala-cli` | ローカルの開発環境 | — | △ 既存。Nix 側の Scala と sbt の版は nixpkgs に従うため、**版は `build.properties` と `build.sbt` で固定**し、Nix は JDK と sbt の入口にとどめる。JDK 21 の追加が要る |
| 整形・静的解析 | scalafmt、scalafix | 最新（sbt プラグイン `sbt-scalafmt` 2.5 系、`sbt-scalafix` 0.14 系は参照実装の値） | 書式の統一、未使用の検出 | — | △ プラグインの版は着手時に確認 |
| コンパイラ設定 | `-feature -deprecation -unchecked`、`-Wunused` | — | 警告の検出 | — | 参照実装の `-no-indent`・`-rewrite`・`-experimental` は**持ち込まない**（TS-12） |

### 2.2 アクター・イベントソーシング

BE-03（イベントストアと配信方式）と BE-04（永続化ライブラリ）の結果で、**A または B の一式**を採ります。共通の部分と、条件付きの部分を分けて書きます。

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 確度・備考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| アクター | Apache Pekko（actor-typed、stream） | 1.5.x | 集約アクター、ストリーム | —（Apache の方針で確認） | ✓ 1.5.0 は 2026-03-23 公開。Akka ではなく Apache 2.0 のフォーク |
| 永続化 | Pekko Persistence Typed、persistence-testkit | Pekko 本体と同じ | イベントの永続化とテスト | — | ✓ |
| クラスター | Pekko Cluster Typed、Cluster Sharding | Pekko 本体と同じ | BE-12。**当面はローカルモード**のため依存に入れない | — | 条件付き |
| ID | airframe-ulid | △ 参照実装は 2025.1.8 | ULID の生成 | — | △ 最新を着手時に確認 |
| **A：参照実装の方式** | `pekko-persistence-dynamodb-journal-v2`、`pekko-persistence-dynamodb-snapshot-v2` | △ 参照実装は 1.0.57 | DynamoDB の Journal・Snapshot | — | **個人のメンテナ**。導入は承認が要る（BE-04）。Pekko 1.5 との適合は PoC で確かめる |
| **A：参照実装の方式** | `pekko-persistence-effector` | △ 参照実装は 0.8.11 | 永続化アクターの記述を簡単にする | — | **個人のメンテナ、1.0 未満**。ラッパーで隔離する（BE-04） |
| **A：配信** | AWS Lambda（コンテナイメージ）、DynamoDB Streams | — | 読み取りモデルの更新 | — | IF-04 |
| **B：標準の方式** | Pekko Persistence JDBC | 1.3.x（2026-06-18 公開） | PostgreSQL に Journal・Snapshot を置く | — | ✓ Apache のプロジェクト |
| **B：配信** | Pekko Projection（core、jdbc） | △ 1.1.x | 購読者ごとの独立したオフセット、読み取りモデルの更新とオフセットを同一トランザクション | — | △ 版は着手時に確認 |

### 2.3 API・HTTP・シリアライズ

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 確度・備考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| HTTP | Pekko HTTP | 1.4.x（2026-07-13 公開） | command-api・query-api・identity-api | — | ✓ |
| GraphQL | Sangria | △ 4.2.x（参照実装は 4.2.4） | コマンド・クエリの API、公開照会は別スキーマ | — | △ 活発に保守されている（最終のコミットは 2026-03）。**公開用の深さ・複雑さの制限が使えるか PoC で確かめる**。代替は Caliban（ZIO 前提のため TS-02 と合わない） |
| JSON | circe（core、generic、parser）、sangria-circe、pekko-http-circe | △ circe 0.14.x、sangria-circe 1.3.x、pekko-http-circe 3.x | GraphQL の入出力 | — | △ 版は着手時に確認 |
| シリアライズ | ScalaPB（runtime）、sbt-protoc | △ ScalaPB 0.11.x、sbt-protoc 1.0.x | イベントの Protobuf（Envelope、版管理） | — | △ **Pekko gRPC は採らない**（TS-06） |
| 設定 | Typesafe Config（HOCON） | Pekko に含まれる | 環境変数による上書き | — | ✓ 参照実装の方式を採る |

### 2.4 データアクセス・マイグレーション

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 確度・備考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| DB | PostgreSQL | **18**（RDS の提供状況で 17 も可） | 読み取りモデル、荷主、認証、冪等性キー | 18：**2030-11**、17：2029-11、16：2028-11 | ✓ 二次情報。着手時に公式で再確認。参照実装は 16.4 |
| DB ドライバ | PostgreSQL JDBC | △ 参照実装は 42.7.x | 接続 | — | △ 最新を着手時に確認 |
| データアクセス | Slick（`slick`、`slick-hikaricp`） | 3.6.1 | 読み取りモデルの DAO | — | ✓ 3.6.1 が最新。Scala 3 対応 |
| DAO 生成 | sbt-dao-generator | △ 参照実装は 1.4.1 | スキーマから DAO を生成 | — | △ **個人のメンテナ**。生成に PostgreSQL（Docker）を要する。代替は手書きの Slick テーブル定義（TS-05） |
| マイグレーション | Flyway（core、`flyway-database-postgresql`） | 12.x | Unit ごとのスキーマのマイグレーション | — | 12.x が最新（二次情報）。参照実装は 10.21.0。**着手時に版を固定** |

### 2.5 テスト・品質

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 確度・備考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| テスト | ScalaTest | 3.2.x の最新 | 単体・統合のテスト | — | △ 参照実装は 3.2.12（古い） |
| プロパティテスト | ScalaCheck（`scalatestplus-scalacheck`） | 最新 | 不変条件（B1〜B12、R1〜R10 など）の性質検査、`RouteFinder` | — | ? ドメインモデルの不変条件に向く |
| アクターのテスト | pekko-actor-testkit-typed、pekko-persistence-testkit | Pekko 本体と同じ | 集約アクターをインメモリの Journal で検査 | — | ✓ |
| 統合テスト | Testcontainers（`testcontainers-scala`） | 最新 | PostgreSQL（と LocalStack）の実物で検査 | — | ? CI で Docker を使う |
| 構造の検査 | ArchUnit | 最新 | 検査 A1〜A3（`domain` が Pekko・Slick を import しない） | — | ? JVM のバイトコードを検査する |
| モック | ScalaMock | △ 参照実装で使用 | 外部ポートのスタブ | — | 手書きのスタブで足りる範囲では使わない |
| カバレッジ | sbt-scoverage（または sbt-jacoco） | 最新 | カバレッジ | — | ? Scala 3 の対応状況を着手時に確認。参照実装は sbt-jacoco |
| ログ | logback-classic、slf4j-api | logback 1.5.x、slf4j 2.x | 構造化ログ（JSON） | — | △ 参照実装は logback 1.3.14・slf4j 1.7.36（古い）。最新を着手時に確認 |

### 2.6 パッケージング

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 確度・備考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| パッケージ | sbt-native-packager（Docker） | △ 参照実装は 1.11.x | コンテナイメージの作成 | — | △ |
| ベースイメージ | `eclipse-temurin:21-jre` | 21 | 実行 | JDK 21 に従う | ✓ 参照実装は `17-jre-focal`。タグでなく**ダイジェストで固定**する（インフラ 4.3） |
| 配布（Lambda） | コンテナイメージ | — | event-processor（方式 A のとき） | — | 参照実装の `sbt-assembly` の fat jar は採らない（IF-04） |

---

## 3. フロントエンド

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 確度・備考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 実行環境 | Node.js（**LTS**） | **24** | ビルド・テスト・開発サーバー | 2028-04 | ✓ 二次情報。リポジトリの既存コンテナは Node 22（`docker-compose.yml` の `NODE_MAJOR: 22`、**2027-04-30** で EOL）。アプリは 24 に揃える |
| 言語 | TypeScript | 5.x の最新 | 型付き開発 | — | ? |
| フレームワーク | React | 19.x の最新 | SPA（FE-01） | — | △ 19 系の最新を着手時に確認 |
| ビルド | Vite | 最新の安定版 | 開発サーバー、バンドル | — | ? 最新を着手時に確認 |
| ルーティング | React Router | 7.x | 画面遷移、`RequireRole` | — | ? フロントエンド設計で未決だった項目。ライブラリとして使う（フレームワークモードの SSR は使わない） |
| サーバー状態 | TanStack Query | 5 系または 6 系 | サーバー状態のキャッシュ、「版が追いつくまで再取得」（FE-02） | — | **要確認**：2026-03 に v6 が公開されたとの情報がある一方、v5 のパッチも続く。**安定した最新のメジャーを着手時に決める** |
| GraphQL | graphql-request、GraphQL Code Generator（client preset） | 最新 | 型付きの問い合わせ、スキーマから型を生成（検査 F3） | — | ? |
| フォーム | React Hook Form、Zod | 最新 | 入力の検証（クライアント側は「その場で分かるもの」だけ） | — | ? 外部ライブラリの導入は承認が要る |
| スタイル | Tailwind CSS | 4.x | ユーティリティクラス | — | ? 最新を着手時に確認 |
| 部品 | Radix UI（プリミティブ） | 最新 | ダイアログ・タブなどのアクセシブルな土台 | — | ? **自前の薄い部品群**にする案（FE-06）。コンポーネントライブラリ一式は採らない |
| 単体テスト | Vitest、Testing Library | 最新 | 部品・フックの検査 | — | ? |
| モック | MSW（`msw`）＋スキーマ検証 | 最新 | API のモック | — | ? **スキーマで応答を検証し、バックエンドより甘いモックにしない**（検査 F4） |
| E2E | Playwright | 最新 | 画面から踏む検査（一覧から対象を選んで始める） | — | ? |
| アクセシビリティ | axe-core（`@axe-core/playwright`） | 最新 | 自動のアクセシビリティ検査 | — | ? |
| 静的解析 | ESLint（flat config）、Prettier | 最新 | 検査 F1・F2（feature の import、GraphQL の直接呼び出しの禁止） | — | ? `eslint-plugin-boundaries` 等 |
| パッケージ管理 | npm | Node 24 に同梱 | 依存の管理 | — | 既存のリポジトリに合わせる（`package-lock.json`） |

> 画面の言語は日本語のみ（UI 設計の仮定）。i18n のライブラリは入れません。

---

## 4. インフラ・運用

| カテゴリ | 技術 | バージョン | 用途 | サポート期限 | 確度・備考 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| クラウド | AWS | — | 実行基盤（IF-01） | — | 承認待ち |
| コンテナ実行 | Amazon ECS（Fargate） | — | command-api・query-api・identity-api | — | EKS は採らない（インフラ 4.2） |
| 関数実行 | AWS Lambda（コンテナイメージ） | — | event-processor（方式 A のとき） | — | IF-04 |
| データ | Amazon RDS for PostgreSQL | **18 または 17** | 読み取りモデル・荷主・認証・冪等性キー | PostgreSQL に従う。**RDS の提供期間は AWS の公表で確認** | ? 提供状況を着手時に確認（TS-10） |
| データ | Amazon DynamoDB | — | イベントストア（方式 A のとき） | — | |
| 入口 | ALB、AWS WAF、CloudFront、S3 | — | 入口・回数制限・SPA の配信 | — | |
| 秘密情報 | AWS Secrets Manager | — | 接続情報（R-10） | — | |
| レジストリ | Amazon ECR | — | イメージの保管（IF-03） | — | |
| 監視 | Amazon CloudWatch（Logs、Metrics、Alarms） | — | 反映遅延などの指標、ログ | — | OpenTelemetry は非機能要件のあとに判断 |
| IaC | Terraform | 1.x の最新 | 環境の構築（IF-02、`operating-provision`） | — | ? **ライセンスに注意**：Terraform 1.6 以降は BSL。OpenTofu（MPL）が代替。版は着手時に確認（検索結果が食い違っていたため数値を置かない） |
| CI/CD | GitHub Actions | — | ビルド・検査・デプロイ | — | △ 既存（`docs-okf.yml`、`mkdocs.yml`、`docker-publish.yml`） |
| クラウド認証 | GitHub OIDC ＋ `aws-actions/configure-aws-credentials` | 最新 | 長期の鍵を置かない認証 | — | ? |
| 秘密情報の検出 | gitleaks（またはGitHub のシークレットスキャン） | 最新 | 検査 I1 | — | ? |
| 脆弱性の走査 | Trivy | 最新 | イメージの走査 | — | ? **公式イメージを直接実行する**（アクションの導入失敗と検出が同じ赤になるのを避ける） |
| 依存の更新 | Dependabot または Renovate、sbt-updates | — | 古い依存の検出 | — | ? |
| 静的解析 | SonarQube（`operating-qt`、`ops/docker/sonarqube-local`） | △ 既存 | 品質ゲート | — | △ Scala の解析とカバレッジの取り込み方は着手時に確認 |
| ローカル | Docker Compose、PostgreSQL（コンテナ）、LocalStack（方式 A のとき） | PostgreSQL は DB と同じ版 | ローカルの再現 | — | △ LocalStack は参照実装が 4.7。**ライセンスと無償の範囲を着手時に確認** |
| 運用タスク | Gulp（`ops/scripts/*.js`） | △ 既存（gulp 5） | `dev:up` などの運用タスク（`operating-script`） | — | △ 既存 |
| 文書 | MkDocs、PlantUML | △ 既存 | 設計文書の公開 | — | △ 既存 |

---

## 5. 技術選定の判断（ADR 候補）

| ID | 判断 | 推奨 | 主な代替 | 関連 | 決定期限 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| TS-01 | 言語と JDK | Scala 3.9 LTS ＋ JDK 21 | Scala 3.3 LTS（JDK 8 互換、2027-09 まで）、JDK 25 | — | Bolt 1 の前 |
| TS-02 | エフェクト（BE-07） | **ZIO を採らず**、`Future` ＋ `Either` | ZIO 2 | BE-07 | Bolt 1 の前 |
| TS-03 | イベントストア系の依存 | BE-03・BE-04 の結果に従う（A または B の一式） | — | BE-03、BE-04 | Bolt 1 の前 |
| TS-04 | GraphQL ライブラリ | Sangria ＋ circe | Caliban | BE-06 | Bolt 1 の前 |
| TS-05 | DAO | 手書きの Slick 定義を基本にし、生成は PoC で採否を決める | sbt-dao-generator | R-03 | Bolt 1 の前 |
| TS-06 | Protobuf | sbt-protoc ＋ ScalaPB のみ（Pekko gRPC は採らない） | Pekko gRPC プラグイン | — | Bolt 1 の前 |
| TS-07 | テスト | ScalaTest ＋ ScalaCheck ＋ Testcontainers ＋ ArchUnit | — | 検査 A1〜A9 | Bolt 1 の前 |
| TS-08 | フロントエンド一式 | React 19 ＋ TypeScript ＋ Vite ＋ TanStack Query ＋ graphql-request ＋ codegen ＋ React Router ＋ Tailwind ＋ Radix | Scala.js、Apollo Client | FE-01、FE-02、FE-06 | Bolt 1 の前 |
| TS-09 | IaC | Terraform | OpenTofu（BSL 回避） | IF-02 | ステージング構築の前 |
| TS-10 | PostgreSQL のメジャー版 | 18（RDS 未提供なら 17） | 16 | IF-01 | ローカル環境の構築の前 |
| TS-11 | Node.js | 24 LTS（既存の 22 は 2027-04-30 に EOL） | 22 | — | フロントエンドの着手前 |
| TS-12 | Scala の書き方 | Scala 3 の既定の構文＋ scalafmt で統一。参照実装の `-no-indent`・`-rewrite`・`-experimental` は使わない | 波括弧に統一 | BE-07 | Bolt 1 の前 |

### 5.1 判断の理由

- **TS-02**：ドメインは `Either[Error, (状態, イベント)]` を返す純粋関数で、Pekko の `ask` と Sangria は `Future` を基本にします。ZIO を足すと `Future` との橋渡し（参照実装の `PekkoInterop`）と、学習の負担が増えます。ユースケースを副作用の記述として扱う利点は、今のユースケースの規模では小さいと判断しました。参照実装を写す AI の生成と食い違うため、**チームの習熟度で人が決める**ことを前提にした提案です
- **TS-05**：参照実装の DAO 生成は、ローカルの PostgreSQL を起動して生成する手順を必要とし、個人製のプラグインに依存します。本プロジェクトの読み取りモデルは 34 テーブルほどで、手書きの定義でも保てる規模です。生成の利点（スキーマとの一致）は、マイグレーションを適用した DB に対する統合テストでも確かめられます
- **TS-06**：参照実装は `PekkoGrpcPlugin` を使いますが、イベントのシリアライズに gRPC のサーバーは要りません。ScalaPB の生成だけにすると依存が減ります。`protoc` の置き場を環境に依存させない構成にできるかは PoC で確かめます（参照実装は `PROTOC_PATH` に固定の既定値を持つ）
- **TS-08**：個々の選定理由はフロントエンド設計の FE-01・FE-02・FE-06 にあります。TanStack Query は、結果整合の画面が「応答の版以上になるまで再取得」するのに向きます
- **TS-09**：リポジトリのスキル（`operating-provision`）は Terraform を前提にしています。ライセンス上の懸念があるため、組織の方針（BSL を許容するか）を確かめてから決めます

---

## 6. 依存の健全性

| 観点 | 内容 | 対応 |
| :--- | :--- | :--- |
| 個人のメンテナの依存 | `pekko-persistence-dynamodb-*`、`pekko-persistence-effector`、`sbt-dao-generator` | ラッパーで隔離する。方式 B（標準の JDBC）に逃げられる設計にする。導入前に承認を得る |
| 参照実装の古い版 | logback 1.3.14、slf4j 1.7.36、ScalaTest 3.2.12、ZIO 2.0.21、Pekko 1.1.3、Scala 3.6.2 | 写さない。最新の安定版を確認して固定する |
| ライセンス | Terraform（BSL）、LocalStack（無償の範囲） | 着手前に確認する |
| 版の固定 | 参照実装は `Dependencies.scala` に `Version` と各ライブラリを集約している | 同じ形（`project/Dependencies.scala`）を採る。バージョンはここだけに書く |
| 更新の検出 | — | Dependabot または Renovate、`sbt-updates`。月に 1 回、更新を確認する |
| 脆弱性 | — | イメージの走査（Trivy）、依存の走査。高深刻度は次のイテレーションを待たず直す |

---

## 7. アップグレード計画

| 技術 | 現在の選定 | 次の節目 | 時期 | 影響と準備 |
| :--- | :--- | :--- | :--- | :--- |
| Scala | 3.9 LTS | 次の LTS | 3.9 系のサポート期限（2029-09 以降）の 1 年前から | 同じ LTS 内のパッチは常に追従。LTS を跨ぐ移行は、依存ライブラリの対応状況を確かめる |
| JDK | 21 | 25（LTS） | 依存ライブラリ（Pekko、Slick、プラグイン）が 25 で CI 緑になった時点。遅くとも 21 の期限（2029-12-31）の 1 年前まで | ベースイメージとビルドの JDK を同時に上げる |
| Pekko | 1.5.x | 1.x の新しいマイナー | 公開のたびに確認し、四半期ごとに追従 | 1.x 内は後方互換を保つ想定。**イベントのシリアライザの互換**を往復テスト（検査 A5）で確かめる |
| PostgreSQL | 18 | 次のメジャー | 毎年のメジャー公開のあと、2 年以内（EOL の 2030-11 の 1 年前まで） | マイグレーション、RDS のメジャーアップグレード手順。読み取りモデルは再構築で代替できる |
| Node.js | 24 LTS | 次の LTS | 24 の EOL（2028-04）の半年前まで | `engines` と CI の版を同時に上げる。既存コンテナの 22 は **2027-04-30 までに** 24 へ |
| React・Vite・TanStack Query | 着手時に固定 | メジャー | 年に 1 回、メジャーの変更点を確認 | 生成コード（codegen）と、`RequireRole`・`AuthProvider` の境界が壊れないか |
| Terraform／OpenTofu | 着手時に固定 | — | 四半期ごと | 状態ファイルの互換に注意（OpenTofu への移行は一方向に近い） |

---

## 8. 未決事項と AI の仮定

### 8.1 人に確認する点

| # | 論点 | 影響 |
| :--- | :--- | :--- |
| Q1 | ZIO を採らず `Future` ＋ `Either` にしてよいか（チームの習熟度） | TS-02、use-case の書き方 |
| Q2 | Scala 3.9 LTS でよいか。依存の個人製ライブラリが 3.9 と JDK 21 で動くかは PoC まで未確認 | TS-01、BE-04 |
| Q3 | Terraform の BSL ライセンスを許容するか | TS-09 |
| Q4 | RDS が PostgreSQL 18 を提供しているか（未提供なら 17） | TS-10 |
| Q5 | フロントエンドの言語（TypeScript でよいか。Scala.js の余地） | TS-08、FE-01 |
| Q6 | 外部ライブラリの一括承認の単位（一覧のどこまでを 1 回で承認するか） | R-01 の承認ゲート |
| Q7 | LocalStack の利用条件（無償の範囲） | 方式 A のローカル環境 |

### 8.2 AI の仮定

- 確認できなかったバージョンは数値を書かず「着手時に最新の安定版を確認する」とした。**数値のない行は、未選定でなく、選定は済んでいて版だけを固定していない**ことを表す
- 二次情報（PostgreSQL・Node.js のサポート期限）は、公式ページで再確認する前提
- ライブラリの組み合わせ（Pekko 1.5 ＋ Scala 3.9 ＋ 個人製プラグイン）の相互の動作は、ウォーキングスケルトン（Bolt 1）の PoC で確かめる

### 8.3 次の設計活動

1. 承認ゲート：外部ライブラリの導入の承認（Q6）。TS-01・TS-02・TS-03・TS-06 は Bolt 1 の前に要る
2. PoC（Bolt 1 の一部）：Pekko 1.5 ＋ Scala 3.9 ＋ JDK 21 で、集約アクターの永続化と読み取りモデルへの投影を通す
3. テスト戦略（`analyzing-test-strategy`）：TS-07 の道具を水準に割り当てる
4. 環境構築（`operating-setup`）：ローカルの Nix シェルに JDK 21 を足し、`project/Dependencies.scala` を作る（承認後）

---

## 9. 出典

確認日はすべて 2026-11-04 です。

- Scala 3.9 LTS：[Scala 3.9 のリリース](https://scala-lang.org/news/3.9/)、[3.9.0 LTS のアナウンス](https://users.scala-lang.org/t/scala-3-9-0-lts-has-been-released/12372)、[3.3.9 LTS](https://scala-lang.org/news/3.3.9/)
- Eclipse Temurin のサポート：[Temurin Support](https://adoptium.net/support)、[endoflife.date の Temurin](https://endoflife.date/eclipse-temurin)
- Apache Pekko：[pekko-http 1.4.0 のリリース目録](https://release-catalog.apache.org/pekko-http/1.4.0/index.html)、[Pekko Persistence JDBC](https://release-catalog.apache.org/pekko-persistence-jdbc/index.html)、[Pekko Persistence JDBC のドキュメント](https://pekko.apache.org/docs/pekko-persistence-jdbc/current/)
- PostgreSQL のバージョン方針（二次情報）：[Instaclustr の解説](https://www.instaclustr.com/education/postgresql/postgres-versions-supported-releases-eol-dates-upgrades/)
- Node.js の LTS 日程（二次情報）：[Node.js End-of-Life の一覧](https://dev.to/endoflifeai/nodejs-end-of-life-dates-official-eol-schedule-for-every-version-6do)
- sbt：[sbt 1.12.12](https://eed3si9n.com/sbt-1.12.12)、[sbt 1.12.13](https://eed3si9n.com/sbt-1.12.13)
- Slick：[Scala Index の Slick](https://index.scala-lang.org/slick/slick/artifacts/slick)
- Sangria：[Sangria のリリース](https://www.github.com/sangria-graphql/sangria/releases)
- 参照実装のバージョン：`tmp/pekko-cqrs-es-example/project/Dependencies.scala`、`project/plugins.sbt`、`build.sbt`、`README.md`（Git 管理外）
- リポジトリの現状：`ops/nix/environments/scala/shell.nix`、`docker-compose.yml`、`package.json`、`docs/article/source/scala/apps/cargo-tracker/project/build.properties`
