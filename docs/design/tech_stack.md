---
title: 技術スタック選定 - 国際貨物輸送管理システム（Flix 版）
description: DDD・ヘキサゴナル・CQRS アーキテクチャを Flix 単一言語 + Java 相互運用で実現するための技術スタック選定と一覧。
published: true
date: 2026-07-31T00:00:00.000Z
tags: design, tech-stack, flix, jvm, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム（Flix 版）

## 概要

本ドキュメントでは、国際貨物輸送管理システムの Flix 実装で採用する技術スタックを一覧化し、選定理由を記録する。

本実装の最大の制約は、**Flix には Web フレームワーク・ORM・モックライブラリ・カバレッジツールが存在しない**ことである。
そのため本プロジェクトは以下の方針を採る。

1. **Flix 単一言語**：ドメイン層・アプリケーション層・アダプタ層のすべてを Flix で記述する。Java 側にロジックを置かない
2. **Java 相互運用は「境界の内側」に閉じる**：JDK 標準 API（`com.sun.net.httpserver`・`java.sql`）と最小限の Maven 依存のみを、Flix のアダプタ実装から呼ぶ
3. **効果（effect）をポートとする**：ヘキサゴナルの出力ポートを Flix の代数的効果として定義し、本番はハンドラで JDBC 実装、テストはハンドラでインメモリ実装に差し替える。モックライブラリを不要にする

この 3 方針により、フレームワークに依存せずヘキサゴナルアーキテクチャを言語機能だけで表現できる。詳細は [バックエンドアーキテクチャ](architecture_backend.md) を参照すること。

## 言語・ランタイム

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Flix | 0.75.1 | アプリケーション実装言語 | 代数的データ型・トレイト・**代数的効果とハンドラ**・Datalog 制約解決を備え、DDD の値オブジェクト／集約とヘキサゴナルのポートを言語機能だけで表現できる | Apache 2.0 | 活発に開発中（0.x 系のため破壊的変更あり） |
| JVM (OpenJDK / Temurin) | 25 (LTS) | 実行基盤 | Flix は JVM バイトコードを生成する。最新 LTS を採用し、サポート期間を最大化する | GPLv2+CE | LTS（2033 年まで） |
| Flix Package Manager | Flix 同梱 | ビルド・依存管理（`flix.toml`） | Flix 標準のビルド／テスト／パッケージ管理。Maven 依存（`maven:...`）と Flix パッケージ依存の両方を宣言できる | Apache 2.0 | Flix に同梱 |

> **バージョン方針**: Flix は 0.x 系であり、マイナーバージョン間で構文・標準ライブラリに破壊的変更が入りうる。
> `flix.toml` の `flix` フィールドでコンパイラバージョンを固定し、アップグレードは独立したコミットで行い ADR に記録する。
> 詳細は `docs/adr/` を参照すること。
>
> **配布形態**: Flix は単一の実行可能 JAR（`flix.jar`）として配布される。パッケージマネージャからは導入できない。
> 本プロジェクトでは `ops/tools/flix/flix.jar` に配置する（Git 管理外）。取得手順は
> [アプリケーション開発環境セットアップ手順書](../operation/アプリケーション開発環境セットアップ手順書.md) を参照。

## HTTP・Web

Flix に Web フレームワークは存在しないため、JDK 内蔵の HTTP サーバを Java 相互運用で駆動し、その上に Flix で薄いルーティング層を自作する。

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `com.sun.net.httpserver` | JDK 25 同梱 | HTTP サーバ | 追加依存ゼロ。本システムの想定同時接続数（[非機能要件](non_functional.md)）では十分な性能。Flix の `IO` 効果から直接扱える | GPLv2+CE | JDK 標準（Java 18 以降 `jdk.httpserver` モジュールとして正式サポート） |
| Flix `Http/Router`（自作） | - | ルーティング・ミドルウェア | メソッド + パスパターンから Flix のハンドラ関数へディスパッチする ADT ベースの薄い層。フレームワーク依存を排除し、ルーティング表自体をテスト対象にできる | 本プロジェクト | - |
| Flix `Html`（自作 DSL） | - | HTML 生成（SSR） | テンプレートエンジンを使わず、`Html` 型の ADT と結合子（combinator）で HTML を構築する。型検査でタグの閉じ忘れが起きず、エスケープを既定にできる | 本プロジェクト | - |
| Bootstrap | 5.3.x | CSS フレームワーク | レスポンシブ・業務系コンポーネントが揃い、[UI 設計](ui_design.md) がそのまま適用できる。CDN ではなく静的リソースとして同梱する | MIT | GA |
| htmx | 2.0.x | 部分更新・動的 UI | SSR を維持したまま追跡ステータスの定期更新・部分描画を実現。JS を最小化でき、`Html` DSL で属性を出力するだけで足りる | BSD 2-Clause | GA |

> **なぜテンプレートエンジンを使わないか**: Thymeleaf 等の Java テンプレートエンジンを相互運用で呼ぶと、
> テンプレート内の式言語が Flix の型検査の外に出てしまい、Flix を選ぶ最大の利点（型と効果による安全性）を失う。
> `Html` ADT による生成は行数こそ増えるが、画面の構造を Flix の関数として合成・単体テストできる。

## データベース・永続化

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番用 RDBMS | ACID 準拠・運用実績。DDD 集約のトランザクション整合性を保証する | PostgreSQL License | GA（EOL: 2028-11） |
| PostgreSQL JDBC Driver | 42.7.5 | DB 接続ドライバ | `flix.toml` の `[mvn-dependencies]` に `"org.postgresql:postgresql" = "42.7.5"` として宣言し、`java.sql` 相互運用から使用する。**`Class.forName` による明示的なドライバ登録が必要**（ServiceLoader が機能しない） | BSD 2-Clause | GA |
| H2 | 2.3.232 | テスト・ローカル用インメモリ DB | PostgreSQL 互換モードで統合テストを高速化する。`jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE` | MPL 2.0 / EPL 1.0 | GA |
| HikariCP | 5.1.0 | コネクションプール | JDBC 接続の生成コストを排除する。`Db` 効果のハンドラ内部に隠蔽し、ドメイン層からは見えない | Apache 2.0 | GA |
| Flyway (Core) | **9.22.3** | DB マイグレーション | バージョン管理されたスキーマ変更。Flix の `main` 起動時に Java 相互運用で `Flyway.migrate()` を 1 度だけ呼ぶ。**10.x は DB 種別サポートが別モジュールに分離され、H2 用モジュールが Maven Central に存在しないため 9.x を採用**（IT1 実測） | Apache 2.0 | GA（Community Edition） |

> **ORM を使わない**: Flix から JPA/MyBatis を使う意味はない（アノテーション・XML マッピングは Flix 側から扱えない）。
> `java.sql.PreparedStatement` を Flix の `Db` 効果ハンドラ内で直接扱い、`ResultSet` → Flix の ADT へのデコードを
> 手書きのマッパー関数として書く。SQL は Flix のソース内に定数として置き、CQRS のクエリ側は JOIN を含む
> 読み取り最適化 SQL を直接記述する。

## セキュリティ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| jBCrypt | 0.4 | パスワードハッシュ | [非機能要件](non_functional.md) の BCrypt 要件を満たす。`[mvn-dependencies]` に `"org.mindrot:jbcrypt" = "0.4"` として宣言し、`Password` 効果のハンドラ内に隔離する | ISC | 安定（機能追加は停止、脆弱性なし） |
| Flix `Auth`（自作） | - | 認証・認可・セッション | Spring Security 相当を自作する。セッション ID は `java.security.SecureRandom` で生成、サーバ側セッションストアに保持。認可はルーティング表で「必要ロール」を宣言し、ミドルウェアで検証する | 本プロジェクト | - |
| Flix `Csrf`（自作） | - | CSRF 対策 | セッション単位のトークンを発行し、`POST`/`PUT`/`DELETE` で検証する。`Html` DSL のフォーム生成関数が hidden フィールドを自動付与する | 本プロジェクト | - |

> **セキュリティ機構の自作はリスクである**。認証・セッション・CSRF は既製フレームワークに任せるのが定石だが、
> Flix には該当実装が存在しない。よって「自作範囲を最小・単純に保つ」「OWASP ASVS L1 のチェックリストで
> レビューする」「[テスト戦略](test_strategy.md) でセキュリティ回帰テストを必須にする」の 3 点で補償する。
> この判断は ADR として記録すること。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Flix `@Test` + `flix test` | Flix 同梱 | 単体・統合テストランナー | Flix 標準のテスト機構。`@Test` を付けた **`Unit \ Assert` 返却関数**を実行する | Apache 2.0 | Flix に同梱 |
| Flix `Assert`（標準ライブラリ） | Flix 同梱 | アサーション | `Assert.assertEq(expected = ..., actual)` 等。可読性が必要な箇所は本プロジェクトの `TestSupport` モジュールで補う | Apache 2.0 | Flix に同梱 |
| **効果ハンドラによるテストダブル** | - | モック・スタブの代替 | Mockito 等は Flix から使えない。出力ポートが効果であるため、テストでは「インメモリ実装のハンドラ」を適用するだけでスタブ化できる。記録用ハンドラを使えば呼び出し検証（スパイ）も可能 | 本プロジェクト | - |
| H2 (in-memory) | 2.3.232 | 統合テストの DB | Testcontainers を Flix から扱うのは相互運用コストが高い。既定は H2 + Flyway、CI の日次ジョブのみ実 PostgreSQL コンテナに対して同じテストを流す | MPL 2.0 / EPL 1.0 | GA |
| JDK `HttpClient` | JDK 25 同梱 | HTTP レベルの統合テスト | 起動したアプリに対して実リクエストを送る。追加依存なしで Controller 相当を検証できる | GPLv2+CE | JDK 標準 |
| JDK `HttpServer`（スタブサーバ） | JDK 25 同梱 | 外部 API 契約テスト | WireMock の代替。ACL ポートの相手先を JDK の HTTP サーバでスタブし、リクエスト／レスポンス契約を固定する | GPLv2+CE | JDK 標準 |
| Playwright | 1.4x | E2E テスト | htmx の部分更新・ポーリングを含む画面の検証。Node.js 側から実行し、アプリはビルド済み JAR を起動する | Apache 2.0 | GA |
| `arch-lint`（自作スクリプト） | - | アーキテクチャテスト | ArchUnit は Flix のモジュール構造を検査できない。`use` / `import` 宣言を走査し、レイヤ・コンテキスト間の依存規約違反を検出する自作チェッカを CI で実行する。**レイヤ判定はモジュール名ではなくディレクトリパスで行う** | 本プロジェクト | - |

> **カバレッジ計測の制約**: Flix にはカバレッジツールが存在せず、JaCoCo は生成バイトコードに対しては動作しても
> Flix ソース行にマッピングできない。よって[テスト戦略](test_strategy.md) では行カバレッジ率を品質ゲートに使わず、
> 「ビジネスルール一覧に対するテストの網羅」をトレーサビリティ表で担保する方式に置き換える。

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Flix CLI | Flix 同梱 | ビルド・テスト・JAR 生成 | `flix check` / `build` / `test` / `build-fatjar`。Gradle/Maven を挟まず単一ツールで完結する。配布用の JAR は **`build-fatjar`** で生成する（`build-jar` は Maven 依存を同梱しないため単体実行できない） | Apache 2.0 | Flix に同梱 |
| Gulp | 5.x | タスクランナー | 既存リポジトリの運用スクリプト基盤に合わせる。Flix CLI・Docker・MkDocs の各コマンドを `dev:*` タスクとして統合する（`ops/scripts/develop.js`）。Flix にウォッチモードがないため、TDD モードは Gulp のファイル監視で実現する | MIT | GA |
| Node.js | 22.x (LTS) | タスクランナー実行基盤 | Gulp・Playwright・MkDocs 連携スクリプトの実行 | MIT | GA（LTS） |
| GitHub Actions | - | CI/CD パイプライン | リポジトリ統合、OIDC による AWS デプロイ | - | GA |
| SonarQube | - | コード品質管理 | **Flix 用アナライザは存在しない**。適用対象は SQL・Dockerfile・JS（Playwright）・YAML に限定し、Flix コードは `arch-lint` と レビューで代替する | LGPL 3.0 | GA（Community Edition） |
| Trivy | 0.5x | 依存・イメージ脆弱性スキャン | `flix.toml` の Maven 依存と Docker イメージを走査する | Apache 2.0 | GA |

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 24.x | コンテナ化 | Flix コンパイラを含むビルドステージと、JRE + JAR のみの実行ステージのマルチステージ構成でイメージを最小化する | Apache 2.0 | GA |
| Docker Compose | 2.x | ローカル開発環境 | アプリ + PostgreSQL + MkDocs をまとめて起動する | Apache 2.0 | Docker 同梱 |
| Terraform | 1.x | IaC | インフラのコード管理・再現性のあるプロビジョニング | BUSL 1.1 | GA |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling | - | GA |
| AWS RDS PostgreSQL | 16.x | マネージド DB | Multi-AZ フェイルオーバー・自動バックアップ | - | GA |
| AWS ALB / ECR / Secrets Manager / CloudWatch / Route 53 / ACM | - | 負荷分散・レジストリ・機密管理・監視・DNS・証明書 | [インフラアーキテクチャ](architecture_infrastructure.md) 参照。実行成果物が JAR である点を除き、言語非依存の構成 | - | GA |

## ドキュメント・開発ツール

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| MkDocs (Material) | 1.x | ドキュメントサイト生成 | 既存リポジトリ構成に準拠 | BSD 2-Clause | GA |
| PlantUML | - | ダイアグラム生成 | UML・ER 図・ワイヤーフレームのコード管理 | GPL 3.0 | GA |
| Nix (flake) | - | 開発環境の再現 | `nix develop .#java` を基底に Flix コンパイラ JAR を取得するシェルを追加する | LGPL 2.1 | GA |
| VS Code + Flix 拡張 | - | IDE | Flix の公式 LSP 実装を利用できる唯一の実用的な開発環境 | MIT | GA |

## 外部システム連携技術

| 外部システム | 連携方式 | 使用技術 | ACL ポート（Flix 効果名） |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | JDK `HttpClient` + 自作 JSON デコーダ | `ExternalRouting` |
| 税関システム | REST API（HTTP/JSON） | 同上 | `CustomsClearance` |
| 決済機関 | REST API（HTTPS） | 同上 | `PaymentGateway` |
| 港湾管理システム | REST API（HTTP/JSON） | 同上 | `PortManagement` |
| 通知システム | REST API（HTTP/JSON） | 同上 | `Notification` |

> **JSON の扱い**: Flix 標準ライブラリに JSON パーサは含まれないため、`"com.fasterxml.jackson.core:jackson-databind"`
> を `[mvn-dependencies]` に宣言して相互運用で使う薄いデコーダ層を `shared/infrastructure/json` に置き、ドメイン層には Flix の ADT のみを渡す。

## バージョン管理方針

### 選定原則

- **Flix**: 0.x 系のため自動追従しない。四半期ごとに最新版で `flix build && flix test` を検証し、通ればアップグレード PR を作る
- **JVM**: LTS のみ。次期 LTS への移行は Flix コンパイラの対応確認後に行う
- **PostgreSQL**: EOL（2028-11）まで 16.x を維持
- **Maven 依存**: 最小限に保つ。追加時は「Flix 単一言語方針を崩さないか」を ADR で判定する

### アップグレード計画

| 技術 | 現行 | 次期 | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| Flix | 0.75.1 | 最新安定版 | 四半期ごと評価 | 構文・標準ライブラリの破壊的変更。全モジュール |
| JVM | 25 LTS | 29 LTS（次期） | 2028 年（Flix 対応確認後） | Docker イメージ、CI |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| Bootstrap / htmx | 5.3.x / 2.0.x | 随時 | マイナー追従 | 静的リソース差し替えのみ |

## 選定理由の総括

1. **Flix 単一言語で DDD を表現する**: 値オブジェクトは ADT とスマートコンストラクタ、集約は不変レコードと純粋な状態遷移関数、
   出力ポートは代数的効果として表現する。フレームワークのアノテーションに依存しないため、ドメイン層は完全に技術非依存となる。

2. **効果ハンドラがモックライブラリを代替する**: ヘキサゴナルの利点（テスト容易性）を、DI コンテナもモックフレームワークもなしに得られる。
   これは Java/Spring 版に対する本実装の最大の優位点である。

3. **不足するエコシステムは「自作 + JDK 標準」で埋める**: Web フレームワーク・テンプレートエンジン・ORM・セキュリティ基盤を持ち込まず、
   JDK 標準 API の薄いラッパとして自作する。Maven 依存は 7 つ（PostgreSQL Driver・H2・HikariCP・Flyway・SLF4J NOP・jBCrypt・Jackson）に抑える。

4. **エコシステム欠落のリスクを明示的に補償する**: カバレッジ計測不可・SonarQube 非対応・セキュリティ自作という 3 つのリスクに対し、
   それぞれトレーサビリティ表・`arch-lint`・OWASP ASVS レビューという代替統制を [テスト戦略](test_strategy.md) に定義する。
