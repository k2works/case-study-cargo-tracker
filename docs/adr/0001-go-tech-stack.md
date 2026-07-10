# ADR 0001: Go 技術スタックの採用

Java/Spring 版設計を移植する Go 版の実装技術スタックを選定する。

日付: 2026-07-11

## ステータス

2026-07-11 承認されました

## コンテキスト

国際貨物輸送管理システム（Cargo Tracker）の Go 版を、参照元の Java/Spring 版設計（DDD + ヘキサゴナル + CQRS、6 コンテキスト構成）を基に設計する。アーキテクチャ思想は維持しつつ、実装技術を Go エコシステムに読み替える必要がある。選定にあたっては、参照元の選定理由（SQL の明示的管理、SSR + htmx によるシンプルな構成、アーキテクチャルールの CI 検証）を継承できることを重視した。

## 決定

以下の技術スタックを採用する。

| 領域 | Java/Spring 版 | Go 版 | 継承した選定理由 |
| :--- | :--- | :--- | :--- |
| 言語 | Java 25 | Go 1.24.x | 静的型付け・低運用コスト |
| Web | Spring MVC | chi v5 + net/http | 標準ライブラリ親和性・明示的ルーティング |
| SSR | Thymeleaf + htmx | html/template + htmx 2.0 | SSR 維持・JS 最小化・contextual auto-escaping |
| データアクセス | MyBatis | sqlc + pgx v5 | SQL の明示的管理・CQRS Read Model 最適化 |
| マイグレーション | Flyway | golang-migrate | バージョン管理された SQL マイグレーション |
| 認証・認可 | Spring Security | alexedwards/scs + 自作 RBAC ミドルウェア + nosurf | セッションベース認証・RBAC・CSRF 保護 |
| DI | Spring DI | コンストラクタインジェクション（main で手動 wiring） | DI コンテナ不要。依存の組み立てを明示化 |
| ドメインイベント | Spring Events | 自作 in-process イベントディスパッチャ | 疎結合なコンテキスト間通信 |
| テスト | JUnit 5 / Mockito / Testcontainers / ArchUnit / WireMock | testing + testify / moq / testcontainers-go / go-arch-lint / httptest | テストピラミッドとアーキテクチャ検証の継承 |
| テスト DB | H2（PostgreSQL 互換モード） | testcontainers-go の実 PostgreSQL に一本化 | Go に H2 相当がなく、実 DB の方が方言差の偽陰性を防げる |
| 品質 | Checkstyle / SpotBugs | golangci-lint + govulncheck | 静的解析・脆弱性スキャン |
| インフラ | AWS ECS Fargate / RDS / Terraform | 同一（イメージのみ distroless 静的バイナリ化） | 運用設計の再利用 |

## 影響

- Go の軽量バイナリ・高速起動により ECS タスクサイズを JVM 比で半減（256 CPU / 512 MB）できる。
- DI コンテナ・アノテーションに依存しないため、ドメイン層は標準ライブラリのみに依存し、フレームワーク非依存性が Java 版より強くなる。
- H2 廃止により統合テストは Docker 環境が前提となる（CI には testcontainers-go を組み込む）。
- 自作コンポーネント（RBAC ミドルウェア・イベントディスパッチャ）はテストで品質を保証する責務がプロジェクト側に生じる。

## コンプライアンス

- go-arch-lint を CI で実行し、ドメイン層の依存制約（infrastructure を import しない等）を継続検証する。
- 技術スタック一覧（docs/design/tech_stack.md）を Single Source of Truth とし、設計・実装で新規ライブラリを導入する際は同ファイルへの追記を必須とする。

## 備考

著者: 開発チーム（Claude Code 支援）。詳細は docs/design/tech_stack.md を参照。
