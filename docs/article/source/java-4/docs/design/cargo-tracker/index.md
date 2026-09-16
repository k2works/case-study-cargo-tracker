# cargo-tracker — 設計

cargo-tracker プロジェクトの設計ドキュメントです。

## ドキュメント一覧

### アーキテクチャ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [バックエンドアーキテクチャ](architecture_backend.md) | Axon Framework 5 による CQRS / Event Sourcing をマイクロサービスとして構成。サービス分割・ヘキサゴナル・投影・Reaction Handler・イベント契約 | 作成済み（draft） |
| [フロントエンドアーキテクチャ](architecture_frontend.md) | React SPA。Command / Query 分離の API クライアント、結果整合性（202 反映中）の UX、ロール別導線 | 作成済み（draft） |
| [インフラストラクチャ](architecture_infrastructure.md) | kind + Kustomize / AWS ECS。Axon Server を全環境で動かし、Event Store のバックアップとリプレイを中心に据える | 作成済み（draft） |

### モデル設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [データモデル設計](data-model.md) | サービスごとの投影テーブル・Axon 管理テーブル・Auth の状態テーブルと Processing Group の対応、リプレイ前提のマイグレーション方針 | 作成済み（draft） |
| [ドメインモデル設計](domain-model.md) | 6 コンテキストの集約・不変条件・コマンド・イベント（内部 / 契約）・状態遷移・Reaction Handler を Event Sourcing 前提で整理 | 作成済み（draft） |

### UI/UX 設計

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [UI 設計](ui_design.md) | 画面一覧・ロール別ナビ・画面遷移・salt 画面イメージ。「反映中」を画面共通の規約として扱い、ボタンの出し分けと集約の判定を同じ遷移表から作る | 作成済み（draft） |

### 品質・運用

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [テスト戦略](test_strategy.md) | ハイブリッド形。集約（AxonTestFixture）・投影と Reaction Handler（Testcontainers）・契約（ゴールデン JSON と往復）・境界（ArchUnit の名簿）・E2E（到達性と反映中）の 5 種と品質ゲート | 作成済み（draft） |
| [非機能要件](non_functional.md) | 反映の遅れの目標値、Axon Server SE の単一障害点を前提にした可用性、crypto-shredding による個人情報の削除、SLI / SLO / SLA、復元演習 | 作成済み（draft） |
| [運用要件](operation.md) | 投影のリプレイを日常操作に、Event Store の復元演習、Event Processor と Reaction Handler の監視、ランブック、イベントの形を変えるリリース、鍵の破棄、Gulp タスク | 作成済み（draft） |

### その他

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [技術スタック](tech_stack.md) | Java 25 / Spring Boot 4.1 / Axon Framework 5.3 / Axon Server 2026.0.4 / MyBatis / PostgreSQL 16 / React 19。IT1 スパイクの確認事項と採用しないもの。90 日で見直し | 作成済み（draft） |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
