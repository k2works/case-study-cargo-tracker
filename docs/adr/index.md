# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [0001](0001-cqrs-read-model-placement.md) | CQRS Read Model の sqlx 実装を infra-persistence に配置し、app 層はクエリポート trait のみ持つ | 承認 |
| [0002](0002-authentication-with-tower-sessions.md) | IT1 の認証を tower-sessions + 自前 RBAC で実装（axum-login からの意図的逸脱） | 承認（IT1 時点） |
| [0003](0003-dependency-injection-composition-root.md) | interface 層の DIP を composition root への依存注入（AppState に出力ポート trait を保持）で回復 | 承認（IT2 時点） |
| [0004](0004-cross-context-write-consistency.md) | BC 跨ぎ書き込みは単一トランザクションで束ねず、各 BC 内整合＋冪等リトライで結果整合に収束させる | 承認（IT4 時点） |
| [0005](0005-booking-status-state-machine.md) | 予約状態遷移を Cargo 集約の &mut self メソッドに閉じ込め、不正遷移を Result::Err で拒否 | 承認（IT4 時点） |
| [0006](0006-tracking-status-derivation-and-cross-context-recovery.md) | 追跡状態を保持イベント列からの純粋関数で導出し、Booking→Tracking 連携の回復戦略（冪等再操作パス・監視検出）を明文化 | 承認（IT5 時点） |

ADR の作成には `creating-adr` スキルを使用してください。
