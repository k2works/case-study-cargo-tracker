# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](./001-java-25-spring-boot-4.md) | Java 25 LTS + Spring Boot 4.0 を採用する | 承認済み |
| [ADR-002](./002-handling-context-tracking-integration.md) | Handling Context を Tracking Context に統合する | 承認済み |
| [ADR-003](./003-database-strategy.md) | 開発は H2、Repository のテストは Testcontainers を使う | 承認済み（2026-08-06 改訂 2 回） |
| [ADR-004](./004-mybatis-not-jpa.md) | 永続化に MyBatis を採用し JPA / Hibernate を採用しない | 承認済み |
| [ADR-005](./005-shared-kernel-scope.md) | 共有カーネルの範囲を Location と ShipperId に限定する | 承認済み |
| [ADR-006](./006-external-integration-internal-simulation.md) | 外部システム連携は実装せず内部シミュレーションで代替する | 承認済み |
| [ADR-007](./007-security-supporting-subdomain.md) | 認証・認可を支援サブドメイン `security` に置く | 承認済み |
| [ADR-008](./008-freight-cost-estimation.md) | 経路候補の費用は概算式で算出する | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
