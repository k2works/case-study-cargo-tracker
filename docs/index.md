# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

> **[ドキュメントポータル](/)** から、コードから生成したドキュメント（JIG・ER 図・マニュアル）や稼働中のアプリケーションにも移動できます。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [記事](./article/index.md) - Cargo Tracker を題材にした 2 シリーズ（多言語実装比較・関数型ドメインモデリング）の入口です。

## コードから生成したドキュメント

`docs/` 配下は「**こう設計した**」を記述したものです。以下はコードと DB スキーマから生成した「**こう実装されている**」です。両者を突き合わせることで、設計と実装の乖離を差分として検出できます。

| 生成物 | 内容 | 突き合わせ先 |
| :--- | :--- | :--- |
| [JIG](/jig/) | ドメインモデル・パッケージ関連・用語集をコードから可視化 | [ドメインモデル設計](./design/domain-model.md) / [バックエンドアーキテクチャ](./design/architecture_backend.md) |
| [ER 図（jig-erd）](/jig-erd/) | Flyway が構築した実スキーマの ER 図 | [データモデル設計](./design/data-model.md) |
| [ユーザーマニュアル](./manual/index.md) | 利用者向けの操作手順（ログイン・荷主管理） | [UI 設計](./design/ui_design.md) |

> これらは**毎回生成するもの**であり、リポジトリにはコミットしていません。生成物をコミットすると「コードを変えたのに図が古い」状態がリポジトリに固定されます。

## ドキュメント構成

| カテゴリ | 概要 | 状況 |
| :--- | :--- | :--- |
| [戦略](./strategy/index.md) | ビジネスアーキテクチャ、インセプションデッキの整理 | 2 件作成済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口 | 4 件作成済み |
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能の整理 | 未作成 |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | **IT1 完了**（9SP / 9SP） |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | `index.md` を整備済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 3 件作成済み |
| [ジャーナル](./journal/index.md) | 判断の経緯と学びの記録 | 1 件作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 7 件承認済み |
| [記事](./article/index.md) | モノリスアーキテクチャ実装比較（13 章）・関数型ドメインモデリング（11 章） | 24 件作成済み |
| [参照元ソース](./article/source/README.md) | 記事が引用する 10 言語の実装コード | 2,855 ファイル配置済み |
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

同一題材（Cargo Tracker）を素材にした 2 シリーズです。扱うユーザーストーリーは US01〜US23 で、上記の要件定義ドキュメントを正典とします。

#### モノリスアーキテクチャ実装比較（Java 軸・10 言語）

10 言語のモノリス実装を、Java の IT1〜IT10 を軸にイテレーション単位で比較します。

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

#### 関数型ドメインモデリング（F# 軸）

F# 実装の IT1〜IT8 を追い、業務ルールをどこまで型に埋め込めるかを検証します。

| ドキュメント | 概要 |
| :--- | :--- |
| [シリーズ概要](./article/functional-domain-modeling/index.md) | 題材・技術スタック・4 つの中核技法 |
| [第 1 章 関数型ドメインモデリングとは](./article/functional-domain-modeling/01-functional-domain-modeling.md) | スマートコンストラクタ・和型・状態機械・ROP・注入ポート |
| [第 2 章 IT1 型で守る土台をつくる](./article/functional-domain-modeling/02-iteration-01.md) | US02 / US03 / US01 |
| [第 3 章 IT2 貨物予約と特殊貨物](./article/functional-domain-modeling/03-iteration-02.md) | US04 / US05 / US06 |
| [第 4 章 IT3 航海スケジュールと経路候補算出](./article/functional-domain-modeling/04-iteration-03.md) | US24 / US25 / US07 / US08 |
| [第 5 章 IT4 経路確定から予約確定まで](./article/functional-domain-modeling/05-iteration-04.md) | US09 / US10 / US11 / US12 / US13 |
| [第 6 章 IT5 追跡と荷役](./article/functional-domain-modeling/06-iteration-05.md) | US14 / US15 / US16 / US17 / US18 |
| [第 7 章 IT6 輸送例外の登録と解決](./article/functional-domain-modeling/07-iteration-06.md) | US19 / US20 |
| [第 8 章 IT7 料金算出と精算](./article/functional-domain-modeling/08-iteration-07.md) | US-ADM-01 / US21 / US22 / US23 |
| [第 9 章 IT8 実務品質への引き上げ](./article/functional-domain-modeling/09-iteration-08.md) | 受入残の充足・契約テスト・通知の実効化 |
| [第 10 章 型で守れたもの・守れなかったもの](./article/functional-domain-modeling/10-summary.md) | 手法の効用と限界 |

#### 参照元ソース

記事中のコード引用はすべて [参照元ソース](./article/source/README.md) の実ファイルから転記しています。10 言語の `apps/` 配下（実装・テスト・ビルド設定・DB マイグレーション）計 2,855 ファイルを収録しています。

MkDocs のビルド対象からは除外しているため（`exclude_docs`）、サイトのページとしては表示されません。リポジトリ上のソースとして参照してください。

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [設計ドキュメントレビュー](./review/設計ドキュメント_review_20260806.md) | `docs/design/` 全 10 ファイルのレビュー結果（高 25 件・中 24 件・低 10 件） |
| [IT1 実装レビュー](./review/IT1実装_review_20260806.md) | IT1 の実装・テスト・マニュアル・CI のレビュー結果（高 10 件・中 12 件・低 14 件） |

## 補足

- `design/`、`development/`、`operation/`、`adr/` は現時点ではカテゴリ索引のみで、実ドキュメントは未作成です。各 `index.md` に作成予定の一覧を記載しています。
- `strategy/`、`requirements/`、`review/` は実ドキュメントを配置済みです。
- `journal/` は作業ログ用の予約ディレクトリです（現在ファイルなし）。
- `strategy/slide/` はスライド生成物の配置先です（現在ファイルなし）。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
- `article/source/` は記事の参照元ソースで、MkDocs のビルド対象外です。
