# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [0001](0001-go-tech-stack.md) | Go 技術スタックの採用（chi + html/template + htmx、sqlc + pgx、scs 等） | 承認 |
| [0002](0002-bounded-context-canon.md) | BC 構成を「7 コンテキスト + Shared Domain」とし正典を domain-model に定義 | 承認 |
| [0003](0003-transport-status-canon.md) | TransportStatus の正典値（9 値）と MISROUTED の RoutingStatus 帰属 | 承認 |
| [0004](0004-discount-rate-limit.md) | 法人荷主の割引率上限を 30% に統一 | 承認（暫定） |

ADR の作成には `creating-adr` スキルを使用してください。
