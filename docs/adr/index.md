# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](001-microservices-architecture.md) | バウンデッドコンテキスト単位のマイクロサービスアーキテクチャの採用 | 承認済み |
| [ADR-002](002-local-kubernetes-kustomize.md) | ローカル開発環境に kind + Kustomize を採用 | 承認済み |
| [ADR-004](004-gateway-jwt-verification.md) | JWT の署名検証は Gateway に一元化し、各サービスはロール認可のみを行う | 承認済み | 2026-08-19 |
| [ADR-003](003-heroku-development-environment.md) | 開発環境（結合テスト）に Heroku Container Registry / Runtime を採用 | 承認済み |

ADR の作成には `creating-adr` スキルを使用してください。
