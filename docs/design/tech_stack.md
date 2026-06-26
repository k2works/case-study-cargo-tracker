---
title: 技術スタック選定 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく技術スタックの選定と一覧。Haskell + Servant 構成の全技術を記録する。
published: true
date: 2026-06-26T00:00:00.000Z
tags: design, tech-stack, haskell, servant, postgresql
---

# 技術スタック選定 - 国際貨物輸送管理システム (Haskell 版)

## 概要

本ドキュメントでは、国際貨物輸送管理システム (Haskell 版) で採用する技術スタックを一覧化し、各技術の選定理由を記録する。
バックエンドアーキテクチャ (DDD + ヘキサゴナル + CQRS)、フロントエンドアーキテクチャ (Lucid SSR + htmx)、
インフラアーキテクチャ (AWS ECS Fargate + RDS PostgreSQL) に基づき、保守性・開発効率・運用性のバランスを重視して選定した。
スタック選定の意思決定の経緯は [ADR 0001](../adr/0001-haskell-servant-stack.md) を参照すること。

## バックエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Haskell (GHC) | 9.10.x | アプリケーション実装言語 | newtype・ADT・型クラスによるドメインモデル表現、コンパイル時の網羅性検査 (`-Wincomplete-patterns`)。GHC 9.10 は最新安定版 | BSD-3-Clause | アクティブメンテナンス |
| Servant | 0.20.x | Web API フレームワーク (型レベル) | API がコンパイル時に型として表現され、ルーティング・ハンドラ・JSON DTO の整合性が型検査される | BSD-3-Clause | GA (アクティブ開発中) |
| Warp | 3.4.x | HTTP サーバー | Haskell エコシステム標準。高速・軽量・本番採用実績多数 | MIT | GA |
| servant-server | 0.20.x | Servant サーバー実装 | API 型からハンドラを束ねる | BSD-3-Clause | GA |
| servant-lucid | 0.9.x | Lucid HTML コンテンツタイプ | Servant ハンドラから直接 Lucid `Html ()` を返却 | BSD-3-Clause | GA |
| servant-auth-server | 0.4.x | 認証 (JWT / Cookie) | Play Session 相当をカスタム `AuthHandler` で実装 | BSD-3-Clause | GA |
| Lucid | 2.11.x | テンプレートエンジン (SSR) | Haskell の monadic DSL で HTML 生成。Twirl/Thymeleaf 相当 | BSD-3-Clause | GA |
| aeson | 2.2.x | JSON シリアライズ | REST API DTO の `ToJSON` / `FromJSON` を `deriving` で自動導出 | BSD-3-Clause | GA |
| http-client + http-client-tls | 0.7.x / 0.3.x | HTTP クライアント | 外部システム連携 (ACL アダプター) の実装 | MIT | GA |
| katip | 0.8.x | 構造化ログ | JSON 構造化ログ。CloudWatch 向け JSON 出力に対応 | BSD-3-Clause | GA |
| mtl | 2.3.x | モナド変換子 (ReaderT) | `ReaderT Env IO` パターンの基盤 | BSD-3-Clause | GA (Haskell コア) |
| text | 2.1.x | 文字列型 | Haskell 標準の Unicode 文字列。`String` は使用しない | BSD-2-Clause | GA (Haskell コア) |
| time | 1.12.x | 日時 | UTC・ローカル時刻の標準型 (`UTCTime`, `Day`) | BSD-2-Clause | GA (Haskell コア) |
| uuid | 1.3.x | UUID 生成 | ID 採番 (`ShipperId`, `EstimateId` 等) | BSD-3-Clause | GA |
| decimal / scientific | - | 厳密 10 進数 | `Money` の補助型・割引率の正確な計算 | BSD-3-Clause | GA |
| bcrypt | 0.0.x | パスワードハッシュ | ログイン認証の bcrypt ハッシュ生成・検証 | BSD-3-Clause | GA |

> **DI に関する注記**: ReaderT パターンによる環境レコードでの配線。Guice 相当のランタイム DI 誤りはなく、
> 配線誤りはコンパイル時に検出される。

## フロントエンド

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Lucid | 2.11.x | テンプレートエンジン (SSR) | バックエンド参照。フロントエンドのビュー層を担う | BSD-3-Clause | GA |
| Bootstrap | 5.3.x | CSS フレームワーク | レスポンシブデザイン、豊富な業務系コンポーネント、学習コストの低さ | MIT | GA (LTS) |
| htmx | 2.0.x | 部分更新・動的 UI | SSR 構成を維持しつつ追跡ステータス自動更新・フォームバリデーション等を実現。JS 最小化 | BSD 2-Clause | GA |
| http-api-data + servant-server | - | フォームバインディング | `FromForm` インスタンスで `Web.FormUrlEncoded` フォームをバインド | BSD-3-Clause | GA |
| Alpine.js | 3.x | 最小限のクライアント JS | htmx で扱えない軽微なインタラクション (必要時のみ) | MIT | GA |

> 静的アセット (Bootstrap / htmx / Alpine.js) は `static/` 配下に直接配置し、`Servant.Server.StaticFiles` で配信する。
> WebJars 相当は使用せず、バージョン更新はファイル差し替えで管理する。

## データベース

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | 16.x | 本番・開発・テスト用 RDBMS | 信頼性・ACID 準拠・運用実績。DDD 集約のトランザクション整合性を保証。全環境で同一エンジン | PostgreSQL License | GA (EOL: 2028-11) |
| postgresql-simple | 0.7.x | データアクセス | SQL QuasiQuoter (`[sql| ... |]`) による SQL 明示管理。CQRS Read Model 最適化に適合 | BSD-3-Clause | GA |
| resource-pool | 0.4.x | コネクションプール | `postgresql-simple` 推奨のプール実装。RDS の `max_connections` と整合させて設定 | BSD-3-Clause | GA |
| dbmate | 2.x | DB マイグレーション | バージョン管理されたスキーマ変更を SQL ファイルベースで運用。言語非依存で CI/CD に組み込みやすい | MIT | GA |

> **テスト DB**: テストは Testcontainers の実 PostgreSQL を使用し、本番とテストの差異を排除する。
> インメモリ SQLite 等は使用しない。

## テスト

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| hspec | 2.11.x | テストフレームワーク | Haskell コミュニティ標準。RSpec ライクな BDD スタイル | MIT | GA |
| hspec-wai | 0.11.x | Servant 統合テスト | WAI Application 上でエンドポイントの入出力・JSON 整合性・認証を検証 | MIT | GA |
| hedgehog | 1.4.x | プロパティベーステスト | `TransportStatus ↔ TrackingStatus` 全網羅マッピング・スマートコンストラクタの検証に使用 | BSD-3-Clause | GA |
| tasty + tasty-hspec + tasty-hedgehog | 1.5.x | テストランナー統合 | hspec / hedgehog を単一ランナーで実行。テスト出力の統一 | MIT | GA |
| testcontainers-hs | 0.5.x | 統合テスト用コンテナ | 実 PostgreSQL を使用したリポジトリの統合テスト | MIT | GA |
| hspec-wai-json | 0.11.x | JSON API アサーション | JSON レスポンスの構造比較 | MIT | GA |
| Playwright | 1.4x | E2E テスト | htmx の動的更新・ポーリングを含む画面の E2E テスト | Apache 2.0 | GA |
| WireMock (Docker) | 3.x | 外部 API スタブ | 外部システムポート (`ExternalRoutingServicePort` 等) のスタブ。Docker コンテナ経由で起動 | Apache 2.0 | GA |

> **モック使用の方針**: ドメイン層は純粋 (副作用なし) であるため、ドメインモデル単体テストにモックは不要。
> アプリケーションサービスのポート (型クラス) は、テスト用に純粋な `State`/`Writer` ベースのインスタンスを用意して差し替える。
>
> **アーキテクチャ規約検証** (バックエンドアーキテクチャ参照):
>
> 1. ドメイン層がインフラ層に依存しないこと
> 2. ドメイン層が Servant / postgresql-simple / aeson の API に依存しないこと
> 3. アプリケーション層がインフラ層を直接参照しないこと (型クラスポート経由)
> 4. 異なる Bounded Context 間の直接参照禁止 (ACL / Event 経由のみ。`Shared` は除く)
>
> CI で `hlint` + `weeder` + 自作 import 規約チェッカ (`stack exec arch-check`) を実行する。

## ビルド・CI/CD

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Stack | 3.x | ビルドツール | Haskell プロジェクトの再現性ある依存解決 (Stackage Resolver) と並列ビルド | BSD-3-Clause | GA |
| Cabal | 3.12.x | パッケージ仕様 | `cargo-tracker.cabal` でパッケージ依存を宣言。Stack の基盤 | BSD-3-Clause | GA |
| fourmolu | 0.16.x | コードフォーマッタ | チーム全体のコードフォーマット統一。Ormolu 派生で設定柔軟 | BSD-3-Clause | GA |
| HLint | 3.8.x | 静的解析・リファクタリング提案 | 慣用的でないコードの検出。カスタムルールで import 規約も検証 | BSD-3-Clause | GA |
| weeder | 2.9.x | デッドコード検出 | 未使用関数・モジュール・export の検出 | BSD-3-Clause | GA |
| hpc | - | コードカバレッジ | GHC 同梱。`stack test --coverage` で HTML/HPC レポート生成 | BSD-3-Clause | GA (Haskell コア) |
| GitHub Actions | - | CI/CD パイプライン | GitHub リポジトリ統合、OIDC 認証による AWS デプロイ、Stack キャッシュ活用 | - | GA |
| SonarQube | - | コード品質管理 | hpc XML レポート取込による可視化。Community Edition で運用 | LGPL 3.0 | GA |

> **Stack キャッシュ**: GitHub Actions では `~/.stack` と `.stack-work` を `actions/cache` で保存し、
> 再ビルド時間を短縮する。

## インフラ

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Docker | 27.x | コンテナ化 | 環境の再現性、マルチステージビルド (Haskell バイナリ → debian/alpine ランタイム) | Apache 2.0 | GA |
| Docker Compose | 2.x | ローカル開発環境 | マルチコンテナ管理 (PostgreSQL + adminer + mailhog) | Apache 2.0 | GA |
| Terraform | 1.x | IaC | インフラのコード管理、再現性のあるプロビジョニング | BUSL 1.1 | GA |
| AWS ECS Fargate | - | コンテナ実行環境 | サーバーレスコンテナ、Auto Scaling、運用負荷軽減。JVM 不要のため 256 CPU / 512 MB から開始可能 | - | GA |
| AWS RDS PostgreSQL | 16.x | マネージドデータベース | Multi-AZ 自動フェイルオーバー、自動バックアップ | - | GA |
| AWS ALB | - | ロードバランサー | HTTPS 終端・`/health` ヘルスチェック・Blue/Green デプロイ対応 | - | GA |
| AWS ECR | - | コンテナイメージレジストリ | GitHub Actions 統合、脆弱性スキャン | - | GA |
| AWS Secrets Manager | - | シークレット管理 | DB 接続情報・JWT 鍵の安全な管理 (全タスクで JWT 鍵を共有) | - | GA |
| AWS CloudWatch | - | 監視・ログ | アプリケーションログ・メトリクス・アラートの統合管理 | - | GA |
| AWS Route 53 | - | DNS | ドメイン管理、ヘルスチェックフェイルオーバー | - | GA |
| AWS ACM | - | TLS 証明書 | HTTPS 証明書の自動更新 | - | GA |

## ドキュメント

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| MkDocs | 1.x | ドキュメントサイト生成 | Markdown ベース、Material テーマ、PlantUML 統合 | BSD 2-Clause | GA |
| PlantUML | - | ダイアグラム生成 | UML 図・ER 図・ワイヤーフレーム (salt) のコードベース管理 | GPL 3.0 | GA |
| Mermaid | 10.x | ダイアグラム生成 | Quadrant Chart 等の Markdown 内インライン図表、MkDocs 統合 | MIT | GA |

## 開発ツール

| 技術名 | バージョン | 用途・役割 | 選定理由 | ライセンス | サポート状況 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| VS Code + Haskell Extension | - | IDE / エディタ | Haskell Language Server (HLS) 統合。型情報・補完・リファクタリング支援 | MIT | GA |
| Haskell Language Server (HLS) | 2.9.x | Language Server | 型推論・診断・コードアクション。GHC 9.10 対応 | Apache 2.0 | GA |
| ghcid | 0.8.x | ホットリロード | ファイル保存時の自動コンパイル・テスト再実行。開発フィードバックを高速化 | BSD-3-Clause | GA |
| Node.js | 22.x (LTS) | 開発タスクランナー | Gulp タスク実行、MkDocs 連携スクリプト | MIT | LTS (EOL: 2027-04) |
| Gulp | 5.x | タスクランナー | 運用スクリプトの統合管理、開発ワークフローの自動化 | MIT | GA |

## 外部システム連携技術

本システムは以下の外部システムと連携する。連携方式と使用技術を記録する。

| 外部システム | 連携方式 | 使用技術 | ACL ポート (型クラス) |
| :--- | :--- | :--- | :--- |
| 外部経路システム | REST API (HTTP/JSON) | http-client + aeson / WireMock (テスト) | `ExternalRoutingServicePort m` |
| 税関システム | REST API (HTTP/JSON) | http-client + aeson / WireMock (テスト) | `CustomsClearancePort m` |
| 決済機関 | REST API (HTTPS) | http-client-tls + aeson / WireMock (テスト) | `PaymentGatewayPort m` |
| 港湾管理システム | REST API (HTTP/JSON) | http-client + aeson / WireMock (テスト) | `PortManagementPort m` |
| 通知システム | REST API (HTTP/JSON) | http-client + aeson / WireMock (テスト) | `NotificationPort m` |

## バージョン管理方針

### 安定版優先選定

本プロジェクトでは以下の方針でバージョンを選定する。

- GHC: 9.10.x を使用し、9.12 リリース後にライブラリ互換性を確認のうえ移行を計画する
- Stack Resolver: LTS スナップショット (例: `lts-23.x`) を使用し、Stackage の検証済み依存解決を活用する
- PostgreSQL: EOL (2028-11) まで 16.x を維持し、17.x への移行は 2027 年を目標とする
- Servant: 0.20.x のパッチバージョンは積極的に追従。0.21 以降のマイナーはリリースノート確認のうえ追従
- ライブラリのバージョンは `stack.yaml` (resolver) と `package.yaml` (extra-deps) に一元管理し、定期的に `stack update` で検証する

### アップグレード計画

| 技術 | 現行バージョン | 次期バージョン | 予定時期 | 影響範囲 |
| :--- | :--- | :--- | :--- | :--- |
| GHC | 9.10.x | 9.12.x | ライブラリ対応確認後 6 ヶ月以内 | コンパイラ警告・拡張機能 |
| Stack Resolver | LTS 23.x | LTS 24.x | LTS GA 後 3 ヶ月以内 | ライブラリバージョン変動 |
| PostgreSQL | 16.x | 17.x | 2027 年 | スキーマ移行 (互換性高) |
| Servant | 0.20.x | 最新 | 随時 | API 型定義の変更確認 |
| dbmate | 2.x | 最新 | 随時 | マイグレーションスクリプト |

## 選定理由の総括

本システムの技術スタック選定は、以下の 4 方針に基づいている。

1. **アーキテクチャとの整合性**: DDD + ヘキサゴナル + CQRS を Haskell エコシステムで自然に実現できる技術を優先した。
   postgresql-simple の SQL QuasiQuoter は CQRS の Read Model 最適化に適合し、Haskell の言語機能 (newtype・ADT・型クラス・網羅性検査) が
   ドメインモデルの型安全な表現を支える。

2. **外部システム分離**: 5 つの外部システム連携をすべて型クラスポートとして抽象化し、http-client と WireMock の組み合わせで
   実装・テストを完結できる構成とした。

3. **テスト容易性**: ドメイン層を `IO` 非依存に保つことでモック不要の単体テストを実現し、HLint カスタムルール + weeder + 自作 import 規約チェッカで依存関係制約をコードベースに継続的に適用する。テスト DB を実 PostgreSQL (Testcontainers) に統一し、本番との差異を排除した。

4. **運用保守性**: AWS マネージドサービス (ECS Fargate / RDS Multi-AZ) を活用し、運用負荷を最小化しながら可用性要件を満たす。
   GHC は最新安定版を採用し、Stack LTS Resolver により依存ライブラリの再現性を確保する。JVM 不要のため、Scala/Java 版より小さなコンテナリソースで開始可能。

## 参照

- [ADR 0001: Servant + Warp 採用](../adr/0001-haskell-servant-stack.md)
- [バックエンドアーキテクチャ](architecture_backend.md)
- [フロントエンドアーキテクチャ](architecture_frontend.md)
- [インフラアーキテクチャ](architecture_infrastructure.md)
- Scala 版参考: `tmp/case-study-cargo-tracker/docs/design/tech_stack.md`
