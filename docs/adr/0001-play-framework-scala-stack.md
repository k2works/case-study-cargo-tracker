# 0001 Scala 版バックエンドスタックとして Play Framework を採用

国際貨物輸送管理システム（Scala 版）のバックエンド技術スタックの選定

日付: 2026-06-12

## ステータス

2026-06-12 承認されました

## コンテキスト

本プロジェクトは、Java/Spring Boot 版（参照実装: `tmp/case-study-cargo-tracker/`）として設計された国際貨物輸送管理システムを Scala で再構築するケーススタディである。

Java 版は以下のアーキテクチャ・技術構成を採用している。

- アーキテクチャ: DDD + ポートとアダプター（ヘキサゴナル）+ CQRS
- バックエンド: Spring Boot 4 + MyBatis（SQL 明示管理）
- フロントエンド: Thymeleaf SSR + htmx 2.x + Bootstrap 5
- インフラ: AWS ECS Fargate + RDS PostgreSQL + Terraform + GitHub Actions

Scala 版の設計にあたり、アーキテクチャ思想（DDD・ヘキサゴナル・CQRS・SSR + htmx）は維持しつつ、これを自然に実現できる Scala の技術スタックを選定する必要があった。候補は以下の 4 案である。

| 案 | 構成 | 評価 |
| :--- | :--- | :--- |
| **A. Play Framework** | Play 3.x（Scala 3）+ Twirl SSR + ScalikeJDBC | SSR・フォーム・CSRF・セッション・ルーティングが標準装備。Spring Boot 版の設計と 1 対 1 で対応付けやすい |
| B. Typelevel スタック | http4s + Cats Effect 3 + doobie + Twirl/Scalatags | 純粋関数型として学習価値は高いが、SSR・フォームバインディング・CSRF・認証まわりの自前構築が増え、本題（DDD のケーススタディ）から焦点が逸れる |
| C. ZIO スタック | ZIO 2 + zio-http + Quill | B と同様にフルスタック Web 機能が弱く、SSR + htmx 構成との相性で Play に劣る |
| D. Spring Boot + Scala | Spring Boot を維持し実装言語のみ Scala 化 | 設計文書の変更は最小だが、Scala のイディオム（イミュータブル設計・Either・opaque type）と Spring の規約が噛み合わず、Scala らしさが限定的 |

## 決定

**案 A: Play Framework 3.x（Scala 3.3 LTS）+ Twirl + ScalikeJDBC を採用する。**

選定理由:

1. **フルスタック Web 機能の標準装備**: SSR（Twirl）・フォームバインディング・CSRF Filter・署名付き Session・型検査されるルーティング（`conf/routes`）が揃っており、Java 版のフロントエンド設計（SSR + htmx）をほぼそのまま移植できる
2. **Java 版設計との対応の取りやすさ**: Spring Boot の各要素（DI・MVC・バリデーション・セキュリティ）に対する対応物が明確で、ケーススタディとして両者を比較学習できる
3. **チーム学習コストの低さ**: MVC スタイルの構成は Spring Boot 経験者が運用可能。エフェクトシステム（IO モナド）の習得を前提としない
4. **ドメイン層の純粋性は言語機能で確保**: ドメインモデルはフレームワーク非依存の Scala 3（イミュータブル case class・opaque type・enum・`Either`）で表現するため、Play への依存はアダプター層に閉じる

併せて以下を採用する。

- **データアクセス**: ScalikeJDBC（SQL interpolation）。MyBatis と同じ「SQL 明示管理」の方針を維持し、CQRS の Read Model 最適化に適合させる
- **DI**: Guice（Play 標準のランタイム DI）。ポート trait → アダプター実装の束ねを `Module.scala` に集約する
- **ドメインイベント**: フレームワーク非依存の `DomainEventPublisher` trait + 同期ディスパッチ実装
- **認証・認可**: Play Session ベースのフォーム認証 + ロール検査 ActionBuilder（カスタム実装）

## 影響

- ランタイムは Pekko（Play 3.x の基盤）となる。Pekko の直接利用は初期フェーズでは行わない
- DI がランタイム DI（Guice）となるため、バインディング誤りは起動時まで検出されない。コンパイル時 DI（macwire 等）への移行は将来の選択肢として残す
- トランザクション境界は `@Transactional` のような暗黙的境界でなく、`DB.localTx` によりアプリケーションサービス層で明示する
- Spring Boot Actuator 相当がないため、ヘルスチェックエンドポイント（`/health`）を自作する
- ドメイン層はエフェクトシステム非依存（`Either` ベース）のため、将来 Typelevel / ZIO スタックへ移行する場合もドメイン層は再利用可能
- 静的解析・カバレッジは scalafmt / scalafix / scoverage に置き換わる（Checkstyle / SpotBugs / JaCoCo の対応物）

## コンプライアンス

- ArchUnit によるアーキテクチャテストで、ドメイン層が Play / ScalikeJDBC / Guice に依存しないことを CI で自動検証する
- 技術スタックの詳細（バージョン・ライセンス）は技術スタック選定ドキュメント（`docs/design/tech_stack.md`、後続作成）に記録する
- 本決定に基づく設計は以下に反映済み
  - [バックエンドアーキテクチャ](../design/architecture_backend.md)
  - [フロントエンドアーキテクチャ](../design/architecture_frontend.md)
  - [インフラアーキテクチャ](../design/architecture_infrastructure.md)

## 備考

- 著者: 開発チーム
- 関連: Java 版参照実装 `tmp/case-study-cargo-tracker/docs/design/`
