# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](0001-集約永続化戦略.md) | Dapper による集約永続化戦略（全削除→再挿入・マルチクエリ再構築・楽観的ロック） | 承認 |
| [ADR-0002](0002-UnitOfWorkとpost-commitイベントディスパッチ.md) | Unit of Work とドメインイベントの post-commit ディスパッチ | 承認 |
| [ADR-0003](0003-開発SQLite本番PostgreSQLの二方言運用.md) | 開発 SQLite / 本番 PostgreSQL の二方言運用（B 案 + 緩和策） | 承認 |

ADR の作成には `creating-adr` スキルを使用してください。
