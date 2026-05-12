# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](./001-heroku-api-routing-and-cors.md) | Heroku 環境の API ルーティングと CORS 設定を環境変数駆動に統一 | 承認済み |
| [ADR-002](./002-vite-dev-proxy-authms-passthrough.md) | 開発環境の Vite dev サーバーで authms を直接プロキシする二段階構成 | 承認済み |
| [ADR-003](./003-cargo-event-publisher-port-adapter.md) | CargoRoutedEvent 発行にポート/アダプタパターンを採用する | 承認済み |
| [ADR-004](./004-testcontainers-rabbitmq-integration-test.md) | RabbitMQ 連携テストに Testcontainers を採用する | 承認済み |
| [ADR-005](./005-tracking-number-issued-event-contract.md) | TrackingNumberIssuedEvent のマイクロサービス間契約管理方針 | 承認済み |
| [ADR-006](./006-heroku-production-profile-setup.md) | 本番 Heroku 環境のプロファイル設計と CloudAMQP 接続セットアップ手順 | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
