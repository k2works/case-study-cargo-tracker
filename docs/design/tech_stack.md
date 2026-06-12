---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく技術スタックの選定と一覧。バックエンド・フロントエンド・インフラ・テスト・ビルドの全技術を記録する。
published: true
date: 2026-06-12T00:00:00.000Z
tags: design, tech-stack, scala, play-framework, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システム（Scala 版）で採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ（DDD + ヘキサゴナル + CQRS）、フロントエンドアーキテクチャ（Twirl SSR + htmx）、
インフラアーキテクチャ（AWS ECS Fargate + RDS PostgreSQL）に基づき、保守性・開発効率・運用性のバランスを重視して選定した。
スタック選定の意思決定の経緯は [ADR 0001](../adr/0001-play-framework-scala-stack.md) を参照すること。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Scala | 3.3.x（LTS） | アプリケーション実装言語 | opaque type・enum・ADT によるドメインモデル表現、コンパイル時の網羅性検査。LTS 系列で安定運用 | Apache 2.0 | LTS（次期 LTS リリース後に移行検討） |
| JDK（Eclipse Temurin） | 21（LTS） | 実行ランタイム | LTS。Play 3.x / ScalikeJDBC の動作要件を満たす。コンテナイメージも Temurin 21 で統一 | GPLv2 + CE | LTS（Temurin サポート 2029 年以降まで） |
| Play Framework | 3.0.x | アプリケーションフレームワーク | SSR（Twirl）・フォーム・CSRF・署名付き Session・型検査されるルーティングを標準装備。Pekko ベース | Apache 2.0 | GA（Play 3.x 系。2.x 系は Akka ライセンス問題のため不採用） |
| Twirl | 2.0.x | テンプレートエンジン（SSR） | Scala にコンパイルされる型安全テンプレート。Play に同梱 | Apache 2.0 | GA（Play に同梱） |
| Apache Pekko | 1.x | 非同期ランタイム（Play 基盤） | Play 3.x の内部基盤。初期フェーズでは直接利用しない | Apache 2.0 | GA（Play に同梱） |
| Guice | 6.x | DI コンテナ | Play 標準のランタイム DI。ポート trait → アダプター実装の束ねを `Module.scala` に集約 | Apache 2.0 | GA（Play に同梱） |
| ScalikeJDBC | 4.3.x | データアクセス | SQL interpolation による SQL 明示管理。CQRS の Read Model クエリ最適化との親和性。Scala 3 対応 | Apache 2.0 | GA（アクティブ開発中） |
| HikariCP | 5.x | コネクションプール | Play JDBC 標準のプール実装。RDS の `max_connections` と整合させて設定 | Apache 2.0 | GA（Play に同梱） |
| Play JSON | 3.0.x | JSON シリアライズ | REST API / htmx 用 DTO の `Format` 導出。Play との統合 | Apache 2.0 | GA |
| Play WS | 3.0.x | HTTP クライアント | 外部システム連携（ACL アダプター）の実装 | Apache 2.0 | GA（Play に同梱） |
| jbcrypt | 0.4 | パスワードハッシュ | ログイン認証の bcrypt ハッシュ生成・検証 | ISC | GA（安定版） |
| Logback + logstash-logback-encoder | 1.5.x / 8.x | ロギング・JSON 構造化ログ | Play 標準の Logback に encoder を追加し、CloudWatch 向け JSON ログを出力 | EPL 1.0 / Apache 2.0 | GA |

> **DI に関する注記**: Guice はランタイム DI のため、バインディング誤りは起動時まで検出されない。
> コンパイル時 DI（macwire 等）への移行は将来の選択肢として ADR 0001 に記録済み。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Twirl | 2.0.x | テンプレートエンジン（SSR） | バックエンドの表を参照。フロントエンドのビュー層を担う | Apache 2.0 | GA（Play に同梱） |
| Bootstrap | 5.3.x | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA（LTS） |
| htmx | 2.0.x | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現、JS 最小化 | BSD 2-Clause | GA（アクティブ開発中） |
| Play Form | 3.0.x | フォームバインディング・検証 | サーバーサイドの形式検証。ドメイン層のスマートコンストラクタと二段構え | Apache 2.0 | GA（Play に同梱） |

> 静的アセット（Bootstrap / htmx）は `public/` 配下に直接配置する（WebJars は使用せず、バージョン更新はファイル差し替えで管理）。

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番・開発・テスト用 RDBMS | 信頼性・ACID 準拠・運用実績。DDD 集約のトランザクション整合性を保証。全環境で同一エンジンを使用 | PostgreSQL License | GA（EOL: 2028-11） |
| PostgreSQL JDBC Driver | 42.7.x | JDBC ドライバ | ScalikeJDBC からの接続 | BSD 2-Clause | GA |
| Flyway + flyway-play | 10.x / 9.x | DB マイグレーション | バージョン管理されたスキーマ変更。flyway-play モジュールで Play 起動時に自動適用 | Apache 2.0 | GA（Community Edition） |

> **テスト DB**: Java 版と異なり H2 は使用しない。テストは Testcontainers の実 PostgreSQL を使用し、
> 本番とテストの差異を排除する（PostgreSQL ネイティブ構文を全環境で使用可能。データモデル設計参照）。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ScalaTest | 3.2.x | テストフレームワーク | Scala 標準のテストフレームワーク。`AnyFunSuite` / `AnyWordSpec` スタイル | Apache 2.0 | GA（アクティブ開発中） |
| ScalaTestPlus-Play | 7.0.x | Play 統合テスト | `FakeRequest` による Controller / routes テスト、Twirl レンダリング検証 | Apache 2.0 | GA（Play 3.x 対応） |
| Mockito（scalatestplus-mockito） | 5.x | モックライブラリ | アプリケーションサービスのポートモック。ScalaTest との統合。Scala 3 対応 | MIT / Apache 2.0 | GA |
| testcontainers-scala | 0.41.x | 統合テスト用コンテナ | 実 PostgreSQL を使用した ScalikeJDBC リポジトリの統合テスト。Scala 3 対応 | MIT | GA（アクティブ開発中） |
| ArchUnit | 1.4.x | アーキテクチャテスト | ヘキサゴナルアーキテクチャの依存関係ルール自動検証（JVM バイトコード検証のため Scala でも利用可） | Apache 2.0 | GA（アクティブ開発中） |
| WireMock | 3.x | 外部 API スタブ | ExternalRoutingServicePort・CustomsClearancePort 等の外部システムスタブ | Apache 2.0 | GA（アクティブ開発中） |
| Playwright | 1.4x | E2E テスト・ブラウザ自動テスト | htmx の動的更新・ポーリングを含む画面の E2E テストに適しているため | Apache 2.0 | GA（アクティブ開発中） |

> **モック使用の方針**: ドメイン層は純粋（フレームワーク・DB 非依存）であるため、ドメインモデルの単体テストにモックは不要。
> モックはアプリケーションサービスのポート（リポジトリ・イベント発行・ACL）に限定する。
>
> **ArchUnit 最低限の検証ルール**（バックエンドアーキテクチャ参照）:
>
> 1. ドメイン層がインフラ層に依存しないこと
> 2. ドメイン層が Play / ScalikeJDBC / Guice の API に依存しないこと
> 3. アプリケーション層がインフラ層を直接参照しないこと（ポート trait 経由）
> 4. 異なる Bounded Context 間でクラスを直接参照しないこと（ACL / Event 経由のみ。`shared` は除く）

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| sbt | 1.10.x | ビルドツール | Scala / Play 標準のビルドツール。Twirl コンパイル・incremental compile・Coursier キャッシュ | Apache 2.0 | GA |
| sbt-native-packager | 1.10.x | 配布物パッケージング | `sbt stage` で起動スクリプト込みの配布物を生成し、Docker マルチステージビルドに使用 | BSD 2-Clause | GA |
| scalafmt（sbt-scalafmt） | 3.8.x / 2.5.x | コードフォーマット | `scalafmtCheckAll` を CI ゲートに。チーム全体でフォーマットを統一 | Apache 2.0 | GA（アクティブ開発中） |
| scalafix（sbt-scalafix） | 0.13.x | 静的解析・リファクタリングルール | 未使用 import 検出・構文ルール。Scala 3 ではセマンティックルールに一部制約あり | BSD 3-Clause | GA（アクティブ開発中） |
| sbt-scoverage | 2.x | コードカバレッジ | ステートメントカバレッジの計測。Scala 3 対応 | Apache 2.0 | GA（アクティブ開発中） |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリとの統合、OIDC 認証による AWS デプロイ、Coursier キャッシュの活用 | - | GA（GitHub マネージド） |
| SonarQube | - | コード品質管理 | 静的解析・カバレッジ計測・Quality Gate による品質担保 | LGPL 3.0 | GA（Community Edition） |

> **SonarQube の Scala 対応に関する注記**: SonarQube の Scala 解析（コミュニティプラグイン sonar-scala 等）は
> メンテナンス状況が安定しないため、品質ゲートの一次防衛線は CI 上の scalafmt / scalafix / scoverage とする。
> SonarQube は scoverage レポートの取込による可視化用途を主とし、導入時に最新のプラグイン対応状況を確認すること。

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 27.x | コンテナ化 | 環境の再現性、マルチステージビルド（sbt stage → JRE イメージ）による本番イメージ最小化 | Apache 2.0 | GA（アクティブ開発中） |
| Docker Compose | 2.x | ローカル開発環境構築 | マルチコンテナ管理（PostgreSQL + adminer + mailhog）、開発環境セットアップの簡素化 | Apache 2.0 | GA（Docker に同梱） |
| Terraform | 1.x | IaC（Infrastructure as Code） | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA（HashiCorp サポート） |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling、運用負荷軽減。Play のステートレス Session と相性が良い | - | GA（AWS マネージド） |
| AWS RDS PostgreSQL | 16.x | マネージドデータベース | Multi-AZ 自動フェイルオーバー、自動バックアップ、運用負荷軽減 | - | GA（AWS マネージド） |
| AWS ALB | - | ロードバランサー | HTTPS 終端・`/health` ヘルスチェック・Blue/Green デプロイ対応 | - | GA（AWS マネージド） |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions との統合、イメージの脆弱性スキャン | - | GA（AWS マネージド） |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・`play.http.secret.key` の安全な管理（全タスクで Session 署名鍵を共有） | - | GA（AWS マネージド） |
| AWS CloudWatch | - | 監視・ログ | アプリケーションログ・メトリクス・アラートの統合管理 | - | GA（AWS マネージド） |
| AWS Route 53 | - | DNS | ドメイン管理、ヘルスチェックフェイルオーバー | - | GA（AWS マネージド） |
| AWS ACM | - | TLS 証明書 | HTTPS 証明書の自動更新 | - | GA（AWS マネージド） |

## ドキュメント

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| MkDocs | 1.x | ドキュメントサイト生成 | Markdown ベース、Material テーマ、PlantUML 統合 | BSD 2-Clause | GA（アクティブ開発中） |
| PlantUML | - | ダイアグラム生成 | UML 図・ER 図・ワイヤーフレーム（salt）のコードベース管理 | GPL 3.0 | GA（アクティブ開発中） |
| Mermaid | 10.x | ダイアグラム生成 | Quadrant Chart 等の Markdown 内インライン図表、MkDocs 統合 | MIT | GA（アクティブ開発中） |

## 開発ツール

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| IntelliJ IDEA + Scala Plugin | - | IDE | Scala 3 / Play / Twirl 対応。リファクタリング支援 | Commercial / Community | GA（JetBrains サポート） |
| Metals | 1.x | Language Server（VS Code 等） | IntelliJ 以外のエディタでの Scala 3 開発支援。sbt BSP 連携 | Apache 2.0 | GA（アクティブ開発中） |
| Node.js | 22.x（LTS） | 開発タスクランナー | Gulp タスク実行、MkDocs 連携スクリプト | MIT | LTS（EOL: 2027-04） |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA |

## 外部システム連携技術

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。

| 外部システム | 連携方式 | 使用技術 | ACL ポート名 |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API（HTTP/JSON） | Play WS / WireMock（テスト） | `ExternalRoutingServicePort` |
| 税関システム | REST API（HTTP/JSON） | Play WS / WireMock（テスト） | `CustomsClearancePort` |
| 決済機関 | REST API（HTTPS） | Play WS / WireMock（テスト） | `PaymentGatewayPort` |
| 港湾管理システム | REST API（HTTP/JSON） | Play WS / WireMock（テスト） | `PortManagementPort` |
| 通知システム | REST API（HTTP/JSON） | Play WS / WireMock（テスト） | `NotificationPort` |

## バージョン管理方針

### LTS 優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- Scala: LTS 系列（3.3.x）を使用し、次期 LTS リリース後に移行を計画する。Scala Next（3.4+ 非 LTS）は採用しない
- JDK: LTS（21）を使用する。次期 LTS（25）への移行はライブラリ（Play / ScalikeJDBC）の対応確認後に行う
- PostgreSQL: EOL（2028-11）まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- Play Framework: 3.0.x のパッチバージョンは積極的に追従する。3.1 以降のマイナーバージョンはリリースノート確認のうえ追従する
- ライブラリのバージョンは `project/Dependencies.scala` に一元管理し、Scala Steward（または Renovate）による更新 PR を CI で検証する

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| Scala | 3.3.x（LTS） | 次期 LTS | 次期 LTS GA 後 6 ヶ月以内 | コンパイラ警告・ライブラリ互換性 |
| JDK | 21（LTS） | 25（LTS） | Play / 主要ライブラリ対応確認後 | JVM 設定、コンテナベースイメージ |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行（互換性高） |
| Play Framework | 3.0.x | 3.x 最新 | 随時 | ルーティング・設定の変更確認 |
| Flyway | 10.x | 11.x | flyway-play 対応後 | マイグレーションスクリプト |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を Play エコシステムで自然に実現できる技術を優先した。
   ScalikeJDBC の SQL interpolation は CQRS の Read Model 最適化に適合し、Scala 3 の言語機能（opaque type・enum・ADT）が
   ドメインモデルの型安全な表現を支える。

2. **外部システム分離**: 5 つの外部システム連携をすべて ACL ポート（trait）として抽象化し、Play WS と WireMock の組み合わせで
   実装・テストを完結できる構成とした。

3. **テスト容易性**: ドメイン層をフレームワーク非依存に保つことでモック不要の単体テストを実現し、
   ArchUnit によるアーキテクチャルールの自動検証で依存関係制約をコードベースに継続的に適用する。
   テスト DB を実 PostgreSQL（Testcontainers）に統一し、本番との差異を排除した。

4. **運用保守性**: AWS マネージドサービス（ECS Fargate / RDS Multi-AZ）を活用し、運用負荷を最小化しながら可用性要件を満たす。
   Scala / JDK ともに LTS 系列を採用し、ライブラリ更新は Scala Steward + CI で機械的に検証する。

## 参照

- [ADR 0001: Play Framework 採用](../adr/0001-play-framework-scala-stack.md)
- [バックエンドアーキテクチャ](architecture_backend.md)
- [フロントエンドアーキテクチャ](architecture_frontend.md)
- [インフラアーキテクチャ](architecture_infrastructure.md)
