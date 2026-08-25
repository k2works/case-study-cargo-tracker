# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [記事](./article/index.md) - Cargo Tracker を題材にした 4 シリーズ（多言語実装比較・関数型ドメインモデリング・実践 AI 駆動開発・XP によるドメイン駆動設計の実践）の入口です。

## ドキュメント構成

| カテゴリ | 概要 | 状況 |
| :--- | :--- | :--- |
| [戦略](./strategy/index.md) | ビジネスアーキテクチャ、インセプションデッキの整理 | 2 件作成済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口 | 4 件作成済み |
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能の整理 | アーキテクチャ 3 件作成済み |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理、リリース完了報告書 | IT9 まで完了（81 SP / 105 SP）・Release 1.0 報告書作成済み・**IT10 計画済み** |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | `index.md` を整備済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 11 件作成済み |
| [ユーザーマニュアル](./manual/index.md) | 業務担当者向けの操作手引き（画面キャプチャは自動生成） | 10 章作成済み |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 25 件作成済み |
| [記事](./article/index.md) | モノリスアーキテクチャ実装比較（13 章）・関数型ドメインモデリング（11 章）・実践 AI 駆動開発（15 章）・XP によるドメイン駆動設計の実践（14 章） | 53 件作成済み |
| [参照元ソース](./article/source/README.md) | 記事が引用する 10 言語の実装コードと java-2 の実装・一次資料 | 3,849 ファイル配置済み |
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
| [ユーザーストーリー](./requirements/user_story.md) | US 31 件・受け入れ基準・トレーサビリティマトリックス（US 採番の正典） |

### 記事ドキュメント

同一題材（Cargo Tracker）を素材にした 4 シリーズです。上記の要件定義ドキュメントを正典とします。扱うユーザーストーリーは、前 2 シリーズが US01〜US23、実践 AI 駆動開発と XP によるドメイン駆動設計の実践が US01〜US36 です。

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

#### 実践 AI 駆動開発（Java take-6 軸）

開発者 1 名と AI エージェントで 20 イテレーション（8 日・US01〜US36・テスト 1,578 件）作り切った実績を、開発プロセスの側から追います。**扱うのは成果物ではなく、AI と組むと何が起きるかです。**

| ドキュメント | 概要 |
| :--- | :--- |
| [シリーズ概要](./article/ai-driven-development/index.md) | 実績サマリー・通底する 5 つの主題・参照元 |
| [第 1 章 AI 駆動開発の全体像](./article/ai-driven-development/01-overview.md) | 体制・4 層の仕組み・20 IT の推移・結論の先出し |
| [第 2 章 Skill 体系で開発プロセスを固定する](./article/ai-driven-development/02-skills.md) | 56 Skill の分類・`CLAUDE.md` との役割分担 |
| [第 3 章 イテレーションのライフサイクル](./article/ai-driven-development/03-iteration-lifecycle.md) | 開始 6 ステップ・クローズ 7 ステップ |
| [第 4 章 Ralph Loop と自律実行の境界](./article/ai-driven-development/04-ralph-loop.md) | 自律実行・end-of-life・人間判断の領域 |
| [第 5 章 TDD と破壊検証](./article/ai-driven-development/05-destructive-verification.md) | 安全装置を壊して赤を確認する。19 IT の空振り実測 |
| [第 6 章 マルチパースペクティブレビュー](./article/ai-driven-development/06-review.md) | XP 5 視点・2 段階運用・エージェント無応答 |
| [第 7 章 規則を検査に落とす](./article/ai-driven-development/07-rules-into-checks.md) | ADR の「何がどこで守るか」・名簿方式の罠 |
| [第 8 章 記憶と負債](./article/ai-driven-development/08-memory-and-debt.md) | エージェントメモリ・ジャーナル・返済枠の運用 |
| [第 9 章 立ち上げ（IT1〜IT3）](./article/ai-driven-development/09-startup.md) | ウォーキングスケルトンの貫通 |
| [第 10 章 一気通貫（IT4〜IT10）](./article/ai-driven-development/10-end-to-end.md) | 予約から追跡まで。Release 1.0 |
| [第 11 章 補完と精算（IT11〜IT15）](./article/ai-driven-development/11-completion-and-billing.md) | Release 1.1・2.0 |
| [第 12 章 整流（IT16〜IT17）](./article/ai-driven-development/12-rectification.md) | ストーリーを書かない回 |
| [第 13 章 出荷と是正（IT18〜IT20）](./article/ai-driven-development/13-shipping.md) | 最後の BC・v2.1.0 出荷・育つ負債 |
| [第 14 章 何が効き、何が効かなかったか](./article/ai-driven-development/14-conclusion.md) | 総括・AI 駆動開発のアンチパターン 10 |

#### XP によるドメイン駆動設計の実践（Java take-6 軸）

同じ実績を**設計の側から**追います。7 BC は一度に立たず、共有カーネルは 4 要素から 2 要素に縮み、BC 間の連携は同期から結果整合に反転しました。**モデルを動かしたのはどのプラクティスか**を 20 回分たどります。

| ドキュメント | 概要 |
| :--- | :--- |
| [シリーズ概要](./article/xp-domain-driven-design/index.md) | 実績サマリー・通底する 5 つの主題・参照元 |
| [第 1 章 XP と DDD をなぜ一緒に語るのか](./article/xp-domain-driven-design/01-xp-and-ddd.md) | 拡張サークルオブライフ・言葉が降りる 6 層・三つの成果物の進め方 |
| [第 2 章 インセプションデッキから境界づけられたコンテキストへ](./article/xp-domain-driven-design/02-inception-to-contexts.md) | 10 の質問が BC の名前を与える・業務領域の分類 |
| [第 3 章 小さなリリースとイテレーション計画](./article/xp-domain-driven-design/03-releases-and-stories.md) | 要件が降りる 4 段・分類が着手順になる・データモデルだけ先に全体 |
| [第 4 章 開発戦略](./article/xp-domain-driven-design/04-development-strategy.md) | 7 局面で TDD の入口を切り替える |
| [第 5 章 受入テストから集約を立ち上げる](./article/xp-domain-driven-design/05-acceptance-to-aggregates.md) | シナリオテスト 8 本・マニュアルを受け入れの関門に |
| [第 6 章 値オブジェクトと不変条件](./article/xp-domain-driven-design/06-value-objects-and-invariants.md) | 名簿方式と正規表現の使い分け・拒む基準 |
| [第 7 章 リファクタリングでモデルが割れる](./article/xp-domain-driven-design/07-refactoring-splits-the-model.md) | ADR-024 の分割と代償・3 つ組の契約 |
| [第 8 章 境界を守る五つの手段](./article/xp-domain-driven-design/08-guarding-boundaries.md) | 共有カーネル・ACL・イベント（判断の反転）・依存の向き・失敗の届け先 |
| [第 9 章 ユビキタス言語はどこで離れるか](./article/xp-domain-driven-design/09-ubiquitous-language.md) | 対訳表と JIG の対・可視化の入力を検査する |
| [第 10 章 設計ドキュメントを実行可能にする](./article/xp-domain-driven-design/10-executable-design-docs.md) | 正典を読ませる・代理指標・ラチェット |
| [第 11 章 継続的インテグレーションが暴いたもの](./article/xp-domain-driven-design/11-continuous-integration.md) | 方言差は両方向・時間でなく回数・真夜中 |
| [第 12 章 ふりかえりが設計を変えた](./article/xp-domain-driven-design/12-retrospectives.md) | Try が検査になる連鎖・「余力次第」の禁止 |
| [第 13 章 XP は DDD に何を与えたか](./article/xp-domain-driven-design/13-conclusion.md) | 総括・育てたもの／先に決めたもの・残った未達 |

#### 参照元ソース

記事中の引用はすべて [参照元ソース](./article/source/README.md) の実ファイルから転記しています。10 言語の `apps/` 配下（実装・テスト・ビルド設定・DB マイグレーション）計 2,855 ファイルと、後半 2 シリーズが引用する `java-2/`（実装・一次資料・マニュアル・JIG 生成物・マニュアルキャプチャ生成スペック）を収録しています。

MkDocs のビルド対象からは除外しているため（`exclude_docs`）、サイトのページとしては表示されません。リポジトリ上のソースとして参照してください。

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [リリース計画レビュー](./review/リリース計画_review_20260819.md) | リリース計画・IT1 計画のレビュー結果（高 15 件・中 15 件・低 6 件） |
| [イテレーション 1 開発成果物レビュー](./review/イテレーション1_review_20260819.md) | IT1 実装のレビュー結果（高 14 件・中 10 件・低 6 件） |
| [イテレーション 2 開発成果物レビュー](./review/イテレーション2_review_20260820.md) | IT2 実装のレビュー結果（高 8 件・中 14 件・低 6 件） |

## 補足

- `design/`、`development/`、`operation/`、`adr/` は実ドキュメントを配置済みです。各 `index.md` に一覧を記載しています。
- `strategy/`、`requirements/`、`review/` は実ドキュメントを配置済みです。
- `journal/` には開発ジャーナル（判断と学びの記録）を置きます。2026-08-19 / 2026-08-20（IT1）・2026-08-25（IT9 クローズ）の 3 件があります。
- `strategy/slide/` はスライド生成物の配置先です（現在ファイルなし）。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
- `article/source/` は記事の参照元ソースで、MkDocs のビルド対象外です。
