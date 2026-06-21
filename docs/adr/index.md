# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [0001](0001-play-framework-scala-stack.md) | Scala 版バックエンドスタックとして Play Framework を採用 | 承認 |
| [0002](0002-bcrypt-and-session-management.md) | 認証のパスワードハッシュに bcrypt を採用しセッションを Play Session で管理する | 承認 |
| [0003](0003-pricing-service-shared-between-estimate-and-billing.md) | 料金計算ドメインサービスを Estimation と Billing で共通化する | 承認 |
| [0004](0004-us26-as-cross-cutting-story.md) | US26（認証・認可）を UC 横断ストーリーとして扱う | 承認 |
| [0005](0005-route-search-algorithm.md) | 経路探索アルゴリズム選定（DFS + 深さ制限、IT2 spike → IT3 US08 で再評価） | 提案 |
| [0006](0006-voyage-data-model-extension.md) | 航海データモデル追補（船名・運送会社・対応貨物種別 + Routing 値オブジェクト分離） | 提案 |

ADR の作成には `creating-adr` スキルを使用してください。
