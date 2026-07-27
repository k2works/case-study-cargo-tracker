# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [0001](0001-go-tech-stack.md) | Go 技術スタックの採用（chi + html/template + htmx、sqlc + pgx、scs 等） | 承認 |
| [0002](0002-bounded-context-canon.md) | BC 構成を「7 コンテキスト + Shared Domain」とし正典を domain-model に定義（0010 で 8 に改訂） | 承認 |
| [0003](0003-transport-status-canon.md) | TransportStatus の正典値（9 値）と MISROUTED の RoutingStatus 帰属 | 承認 |
| [0004](0004-discount-rate-limit.md) | 法人荷主の割引率上限を 30% に統一 | 承認（暫定） |
| [0005](0005-bc-reference-and-shared-sqlcgen.md) | BC 間参照は業務識別子（shipper_code）・共有 sqlcgen の扱い | 承認（暫定） |
| [0006](0006-shared-cargo-type-and-voyage-model.md) | CargoType を共有カーネルへ昇格・航海スケジュールのモデル拡張 | 承認 |
| [0007](0007-route-search-cross-bc-acl.md) | 経路探索の BC 横断を合成ルート注入方式で実現・探索アルゴリズムの段階実装 | 承認 |
| [0008](0008-bc-sync-consistency-boundary.md) | BC 間状態同期（追跡番号発行・荷役イベント）の整合性境界を同期 in-process + 明示的既知制約とする | 承認（暫定） |
| [0009](0009-tracking-exception-design.md) | 追跡例外を集約内エンティティで管理・通知ベストエフォート・エスカレーションは登録時 1 回評価 | 承認（暫定） |
| [0010](0010-discount-policy-context.md) | 割引ポリシー管理コンテキストを新設し BC 正典を 8 に更新（ADR-0002 改訂） | 承認 |

ADR の作成には `creating-adr` スキルを使用してください。
