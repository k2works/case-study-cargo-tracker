# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [記事](./article/index.md) - モノリスアーキテクチャ実装比較（10 言語）の入口です。

## ドキュメント構成

| カテゴリ | 概要 | 状況 |
| :--- | :--- | :--- |
| [戦略](./strategy/index.md) | ビジネスアーキテクチャ、インセプションデッキの整理 | 2 件作成済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口 | 4 件作成済み |
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能の整理 | 未作成 |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | `index.md` を整備済み |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | `index.md` を整備済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 1 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | `index.md` を整備済み |
| [記事](./article/index.md) | モノリスアーキテクチャ実装比較（全 13 章） | 13 件作成済み |
| [リファレンス](./reference/index.md) | 開発ガイドラインやベストプラクティス | 30 件のドキュメントを配置 |
| [テンプレート](./template/index.md) | 各種ドキュメントの作成テンプレート | 18 件のテンプレートを配置 |

### 戦略ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ビジネスアーキテクチャ](./strategy/business_architecture.md) | ビジネスモデル・バリューストリーム・ケイパビリティ・ビジネスシナリオ |
| [インセプションデッキ](./strategy/inception-deck.md) | プロジェクトの目的・スコープ・リスク・ロードマップ（10 の問い） |

### 要件定義ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [要件定義書](./requirements/requirements_definition.md) | RDRA 2.0 に基づく 4 層（システム価値・外部環境・境界・内部構造） |
| [ビジネスユースケース](./requirements/business_usecase.md) | 業務レベル BUC 21 件・アクター目的リスト |
| [システムユースケース](./requirements/system_usecase.md) | システム境界 UC 19 件（完全形式） |
| [ユーザーストーリー](./requirements/user_story.md) | US 27 件・受け入れ基準・トレーサビリティマトリックス |

### 記事ドキュメント

同一題材（Cargo Tracker）の 10 言語モノリス実装を、Java の IT1〜IT10 を軸にイテレーション単位で比較します。扱うユーザーストーリーは US01〜US23 で、[要件定義ドキュメント](#要件定義ドキュメント) を正典とします。

| ドキュメント | 概要 |
| :--- | :--- |
| [シリーズ概要](./article/monolith-architecture/index.md) | 題材・対象言語 10 種のスタック一覧・前提知識 |
| [第 1 章 モノリスアーキテクチャの全体像](./article/monolith-architecture/01-architecture.md) | DDD + ヘキサゴナル + CQRS、Bounded Context、言語別モジュール分割 |
| [第 2 章 IT1 荷主登録と貨物予約の基盤](./article/monolith-architecture/02-iteration-01.md) | US02 / US03 / US04 |
| [第 3 章 IT2 特殊貨物と予約確定](./article/monolith-architecture/03-iteration-02.md) | US05 / US13 |
| [第 4 章 IT3 輸送見積と経路設計への引き渡し](./article/monolith-architecture/04-iteration-03.md) | US01 / US06 |
| [第 5 章 IT4 航海スケジュール検索と経路候補算出](./article/monolith-architecture/05-iteration-04.md) | US07 / US08 |
| [第 6 章 IT5 経路の選択・確定・紐付け](./article/monolith-architecture/06-iteration-05.md) | US09 / US10 / US11 |
| [第 7 章 IT6 法人割引と精算処理](./article/monolith-architecture/07-iteration-06.md) | US22 / US23 |
| [第 8 章 IT7 追跡番号発行と荷役作業記録](./article/monolith-architecture/08-iteration-07.md) | US14 / US15 |
| [第 9 章 IT8 引取記録・追跡照会・状態手動更新](./article/monolith-architecture/09-iteration-08.md) | US16 / US17 / US18 |
| [第 10 章 IT9 遅延・破損・紛失の例外処理](./article/monolith-architecture/10-iteration-09.md) | US19 / US20 |
| [第 11 章 IT10 輸送料金算出とリリース 2.0](./article/monolith-architecture/11-iteration-10.md) | US21 |
| [第 12 章 10 言語横断まとめ](./article/monolith-architecture/12-comparison.md) | 型システム・テスト・境界防御・運用の総括 |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |

## 補足

- `strategy/`、`requirements/`、`design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
