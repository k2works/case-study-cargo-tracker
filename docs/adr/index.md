# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [0001](0001-bounded-context-and-packwerk-structure.md) | Bounded Context は 8 コンテキスト、Packwerk 配置は `packs/<context>/app/...`、AR 依存禁止は RuboCop カスタム cop で担保 | 承認 |
| [0002](0002-domain-events-and-notification.md) | 通知はドメインイベント駆動 + notifications テーブルで送信記録、基盤は ActiveSupport::Notifications（将来 Outbox 移行） | 承認 |
| [0003](0003-cross-context-identifier-and-acl.md) | 越境識別子は shippers.id、ACL はインプロセスアダプタ（将来 HTTP）、Packwerk privacy で公開面を強制、越境 DB は当面物理 FK | 承認 |

ADR の作成には `creating-adr` スキルを使用してください。
