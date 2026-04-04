# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](001-java-springboot-version-strategy.md) | Java 25 LTS / Spring Boot 4.0 採用と移行ロードマップ | 承認済み |
| [ADR-002](002-transactional-event-listener.md) | ドメインイベントに @TransactionalEventListener(AFTER_COMMIT) を使用する | 承認済み |
| [ADR-003](003-discount-policy-as-entity.md) | DiscountPolicy をエンティティとして設計し、ドメインサービスへの昇格を Phase 2 以降に保留 | 承認済み |
| [ADR-004](004-shipper-self-service-out-of-scope.md) | 荷主セルフサービス機能を Phase 1 スコープ外とする | 承認済み |
| [ADR-005](005-windows-docker-desktop-testcontainers.md) | Windows Docker Desktop では Testcontainers を docker_engine_linux に接続する | 承認済み |
| [ADR-006](006-enable-h2-console-on-spring-boot-4.md) | Spring Boot 4 の開発環境では H2 Console 専用モジュールと security 例外を明示設定する | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
