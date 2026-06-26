# 0001 Haskell 版バックエンドスタックとして Servant + Warp を採用

国際貨物輸送管理システム（Haskell 版）のバックエンド技術スタックの選定

日付: 2026-06-26

## ステータス

2026-06-26 承認されました

## コンテキスト

本プロジェクトは、Java/Spring Boot 版および Scala/Play 版（参照実装: `tmp/case-study-cargo-tracker/`）として設計された国際貨物輸送管理システムを **Haskell** で再構築するケーススタディである。

参照実装は以下のアーキテクチャ・技術構成を採用している。

- アーキテクチャ: DDD + ポートとアダプター（ヘキサゴナル）+ CQRS
- バックエンド (Java): Spring Boot 4 + MyBatis（SQL 明示管理）
- バックエンド (Scala): Play Framework 3.x + Twirl SSR + ScalikeJDBC
- フロントエンド: SSR + htmx 2.x + Bootstrap 5
- インフラ: AWS ECS Fargate + RDS PostgreSQL + Terraform + GitHub Actions

Haskell 版の設計にあたり、アーキテクチャ思想（DDD・ヘキサゴナル・CQRS・SSR + htmx）は維持しつつ、これを Haskell エコシステムで自然に実現できる技術スタックを選定する必要があった。候補は以下の 4 案である。

| 案 | 構成 | 評価 |
| :--- | :--- | :--- |
| **A. Servant + Warp** | Servant + Warp + Lucid SSR + postgresql-simple + ReaderT パターン | 型による API 契約・軽量・SSR/HTML EDSL との統合が自然。Play 版の設計と 1 対 1 で対応付けやすい |
| B. Yesod | Yesod + Hamlet + Persistent | フルスタックで Twirl 相当の機能が揃うが、型クラスベースのルーティングと Persistent ORM が「SQL 明示管理」「ポート/アダプター」方針と噛み合いにくい |
| C. Scotty | Scotty + Lucid + postgresql-simple | 最も軽量だが、ルーティング型安全性で Servant に劣り、認証・OpenAPI 等の周辺エコシステムも薄い |
| D. IHP | IHP + ihp-* スタック | 高生産性だが独自フレームワークの規約が強く、DDD/ヘキサゴナルのケーススタディとしてはフレームワーク色が強すぎる |

エフェクトシステムの候補も並行して評価した。

| 案 | 評価 |
| :--- | :--- |
| **ReaderT Env IO パターン** | シンプル・実用的・型クラスポートと相性が良い。Scala 版 DI に対応付けやすい |
| mtl (MonadReader / MonadError) | 型クラスで抽象化可能だが、n+k 問題と制約の組み合わせ爆発リスク |
| effectful / polysemy | 純粋で柔軟だが学習コスト高。ケーススタディとして焦点を逸らす |

永続化ライブラリの候補:

| 案 | 評価 |
| :--- | :--- |
| **postgresql-simple + 生 SQL** | ScalikeJDBC / MyBatis の「SQL 明示管理」と同じ思想。QuasiQuoter で型安全な SQL 補間 |
| Persistent + Esqueleto | 型安全 ORM だが ORM 抽象化が CQRS の Read Model 最適化と相性が悪い |
| Hasql | 高性能・明示的だが学習コストとエコシステム規模で postgresql-simple に劣る |

## 決定

**案 A: Servant + Warp + Lucid SSR + postgresql-simple + ReaderT パターンを採用する。**

選定理由:

1. **型による API 契約**: Servant では API がコンパイル時に型として表現される。ルーティング・ハンドラ・JSON DTO の整合性がコンパイル時に検証され、Play の `conf/routes` 相当の安全性をより強く保証できる
2. **Haskell エコシステムの中核**: Servant + Warp は Haskell Web 開発の事実上の標準であり、認証 (`servant-auth`)、OpenAPI (`servant-openapi3`)、Lucid 統合 (`servant-lucid`) 等の周辺ライブラリが揃っている
3. **参照実装設計との対応**: Play / Spring Boot の各要素 (ルーティング・JSON・SSR・トランザクション) に対する Haskell 側の対応物が明確で、ケーススタディとして 3 言語を比較学習できる
4. **ドメイン層の純粋性は言語機能で確保**: Haskell は副作用が型で表現されるため、`IO` を含まない純粋なドメイン関数として表現できる。フレームワークへの依存はアダプター層に閉じる
5. **学習コストの抑制**: ReaderT パターンは「環境レコードを Reader で渡し、効果は IO」というシンプルなモデルで、effectful / polysemy のような代数的効果の学習を前提としない

併せて以下を採用する。

- **データアクセス**: postgresql-simple + `[sql| ... |]` QuasiQuoter。ScalikeJDBC / MyBatis と同じ「SQL 明示管理」を維持し、CQRS の Read Model 最適化に適合させる
- **DI**: ReaderT Env IO + 環境レコードによるポート実装の配線。`Module.scala` 相当は `App.hs` の `buildEnv` に集約
- **出力ポート**: 型クラス (`class Monad m => CargoRepository m where ...`) で定義し、`AppM` のインスタンスをインフラ層で提供
- **SSR**: Lucid (HTML EDSL)。Twirl/Thymeleaf 相当を Haskell 関数として記述。型不一致はコンパイル時に検出
- **ドメインイベント**: フレームワーク非依存の `DomainEventPublisher` 型クラス + 同期ディスパッチ実装
- **認証・認可**: `servant-auth` ベースの JWT または HMAC 署名付き Cookie + カスタム `AuthHandler` でロール検査
- **設定**: dhall または `envparse` による型安全な設定読み込み
- **ログ**: `katip` による JSON 構造化ログ
- **ビルド**: Stack (推奨) または Cabal
- **マイグレーション**: `dbmate` または `postgres-migrations` による SQL ファイルベース運用

## 影響

- ランタイムは Warp (高速 HTTP サーバー) となる。JVM 不要のため、Scala/Java 版より小さな ECS リソース (256 CPU / 512 MB) で開始可能
- DI が型と環境レコードによる静的な配線となるため、Guice のような実行時バインディング誤りは発生しにくい
- トランザクション境界はアプリケーションサービス層で `withTransaction` ヘルパーにより明示する。`@Transactional` のような暗黙的境界はない
- Spring Boot Actuator / Play 標準ヘルスチェック相当がないため、`/health` エンドポイントを自作する (DB 疎通含む)
- ドメイン層は効果システム非依存 (`Either DomainError a` ベース) のため、将来 effectful / polysemy へ移行する場合もドメイン層は再利用可能
- 静的解析・カバレッジは HLint / fourmolu / weeder / hpc に置き換わる (Checkstyle/scalafmt / SpotBugs/scalafix / JaCoCo/scoverage の対応物)
- Servant の型レベル API 定義は学習コストがあるが、一度習得すると API ドキュメント生成・型レベルテスト等の波及効果が大きい
- Twirl のテンプレートエンジンと異なり、Lucid は Haskell コードとしてそのまま記述するため、テンプレートファイルとロジックの行き来が不要

## コンプライアンス

- 自作 import 規約チェッカ (`stack exec arch-check`) と HLint カスタムルールにより、ドメイン層が Servant / postgresql-simple / aeson に依存しないことを CI で自動検証する
- 異なる Bounded Context 間の直接参照禁止 (ACL / Event 経由のみ) もモジュール依存検査で強制する
- 技術スタックの詳細 (バージョン・ライセンス) は技術スタック選定ドキュメント (`docs/design/tech_stack.md`、後続作成) に記録する
- 本決定に基づく設計は以下に反映済み
  - [バックエンドアーキテクチャ](../design/architecture_backend.md)
  - [フロントエンドアーキテクチャ](../design/architecture_frontend.md)
  - [インフラアーキテクチャ](../design/architecture_infrastructure.md)

## 備考

- 著者: 開発チーム
- 関連:
  - Java 版参照実装 `tmp/case-study-cargo-tracker/docs/design/`
  - Scala 版 ADR 0001 `tmp/case-study-cargo-tracker/docs/adr/0001-play-framework-scala-stack.md`
