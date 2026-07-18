# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [0001](0001-cqrs-read-model-placement.md) | CQRS Read Model の sqlx 実装を infra-persistence に配置し、app 層はクエリポート trait のみ持つ | 承認 |
| [0002](0002-authentication-with-tower-sessions.md) | IT1 の認証を tower-sessions + 自前 RBAC で実装（axum-login からの意図的逸脱） | 承認（IT1 時点） |

ADR の作成には `creating-adr` スキルを使用してください。
