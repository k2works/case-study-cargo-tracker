# ADR (Architecture Decision Records)

アプリケーション開発環境セットアップにおける技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](001-spring-boot-4-java-25.md) | Spring Boot 4.0.5 + Java 25 を採用する | 承認済み |
| [ADR-002](002-gradle-groovy-dsl.md) | ビルドツールに Gradle 9.x (Groovy DSL) を採用する | 承認済み |
| [ADR-003](003-spotbugs-ignore-failures-java25.md) | SpotBugs を ignoreFailures=true で運用する（Java 25 対応） | 承認済み |
| [ADR-004](004-swagger-ui-conditional.md) | Swagger UI を環境変数で条件付き有効化する | 承認済み |
| [ADR-005](005-husky-lint-staged-precommit.md) | Husky + lint-staged で pre-commit 品質チェックを実施する | 承認済み |
| [ADR-006](006-testcontainers-singleton-pattern.md) | Testcontainers でシングルトンコンテナパターンを採用する | 承認済み |
| [ADR-007](007-playwright-e2e-pom-pattern.md) | Playwright E2E テストで Page Object Model パターンを採用する | 承認済み |
| [ADR-008](008-sonarqube-local-quality-gate.md) | ローカル SonarQube で品質ゲートを管理する | 承認済み |
| [ADR-009](009-github-actions-ci-pipeline.md) | GitHub Actions で Build & Test + E2E の 2 ジョブ CI を構成する | 承認済み |
| [ADR-010](010-practical-ddd-package-structure.md) | Practical DDD in Enterprise Java のパッケージ構成を採用する | 承認済み |
| [ADR-011](011-archunit-hexagonal-rules.md) | ArchUnit でヘキサゴナルアーキテクチャの依存関係ルールを自動検証する | 承認済み |
| [ADR-012](012-default-profile-login-prefill.md) | デフォルトプロファイルでログインフォームに認証情報をプリセットする | 承認済み |
| [ADR-013](013-handling-merged-into-tracking-context.md) | 荷役（Handling）機能を Tracking Context に統合する | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
