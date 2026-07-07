# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [ジャーナル](./journal/index.md) - セッションでの判断と学びの物語です。
- [記事](./article/index.md) - 学習用の記事シリーズの入口です。

## ドキュメント構成

| カテゴリ | 概要 | 状況 |
| :--- | :--- | :--- |
| [戦略](./strategy/index.md) | ビジネスアーキテクチャ、インセプションデッキの整理 | 2 件作成済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口 | 4 件作成済み |
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能、運用、技術スタック | 10 件作成済み (Haskell 版) |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | リリース計画 + IT1 計画 + 分析完了報告書 |
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 4 段階セットアップ手順書を配置 |
| [ジャーナル](./journal/index.md) | セッションでの判断・学びの物語形式の記録 | 7 件 (2026-06-26〜07-07) |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 11 件作成済み (分析 3 + IT1-IT8) |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 15 件作成済み (ADR 0001-0016) |
| [記事](./article/index.md) | 学習用の記事シリーズ一覧 | `index.md` を整備済み |
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
| [ユーザーストーリー](./requirements/user_story.md) | US 25 件・受け入れ基準・トレーサビリティマトリックス |

### 設計ドキュメント (Haskell 版)

| ドキュメント | 概要 |
| :--- | :--- |
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | DDD + ヘキサゴナル + CQRS を Servant + Warp + ReaderT で実装 |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | Lucid SSR + htmx + Bootstrap 5 |
| [インフラアーキテクチャ](./design/architecture_infrastructure.md) | AWS ECS Fargate + RDS PostgreSQL + Terraform |
| [ドメインモデル設計](./design/domain-model.md) | 8 境界付けられたコンテキスト・7 集約の戦術的設計 |
| [データモデル設計](./design/data-model.md) | 20 テーブル・postgresql-simple マッピング規約 |
| [UI 設計](./design/ui_design.md) | 24 画面・OOUX・Lucid ビュー構成 |
| [技術スタック](./design/tech_stack.md) | GHC 9.10 + Stack + hspec + hedgehog 等の選定 |
| [テスト戦略](./design/test_strategy.md) | ピラミッド型 (Domain 95% / 全体 85%) |
| [非機能要件](./design/non_functional.md) | ISO/IEC 25010 準拠 SLA / SLO / セキュリティ |
| [運用要件](./design/operation.md) | 監視・バックアップ・障害対応・変更管理 |

### ADR ドキュメント

ADR 0001-0016 の 15 件を [ADR インデックス](./adr/index.md) で管理しています。主要なもの:

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR 0001](./adr/0001-haskell-servant-stack.md) | Haskell 版バックエンドスタックとして Servant + Warp を採用 |
| [ADR 0002](./adr/0002-arch-check-implementation.md) | arch-check による構造規約の CI 強制 (Rule 1-6 / T-01〜03) |
| [ADR 0012](./adr/0012-transaction-boundary-and-cross-bc-policy.md) | Tx 境界と Cross-BC 参照ポリシー (Single Tx / Text-DTO / 副作用外出し) |
| [ADR 0016](./adr/0016-role-based-authorization-design.md) | Role ベース認可の Domain/Interfaces 分離 (採用、Release 2.0 GA) |

### 開発計画ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [リリース計画](./development/release_plan.md) | 25 US / 73 SP・4 フェーズ × 8 IT (0.1 → 0.2 → 1.0 MVP → 2.0 GA) |
| [分析フェーズ完了報告書](./development/analysis_completion_report.md) | 全 16 ドキュメントの成果サマリ・主要決定事項・残課題 |
| [リリース完了報告書 v2.0.0](./development/release_report-2_0_0.md) | Release 2.0 GA 総括 (全 4 Phase / 8 IT)。計画 78 SP → 実績 200+ SP (211%)、438 コミット、889 tests + E2E 29、工期短縮 93% |
| [IT1 計画](./development/iteration_plan-1.md) | AUTH / US02 / US03 / US04 / US24 の 20 SP イテレーション計画 |
| [IT1 完了報告書](./development/iteration_report-1.md) | 成功基準 6 件中 5 OK / 1 未達 (HPC 未計測)、主要メトリクス・学び |
| [IT1 ふりかえり (KPT)](./development/retrospective-1.md) | Keep 13・Problem 14・Try 18 (必達 10 + 推奨 8) |
| [IT2 計画](./development/iteration_plan-2.md) | US01 / US04+US05 / US06 / US25 の 10 SP + Try 必達 8 SP、Release 0.1 Internal Alpha 計画 |
| [IT2 完了報告書](./development/iteration_report-2.md) | 本体 4/4 ストーリー + Try 10/10 完了、207 tests / 0 failures、arch-check Phase 2 は IT3 繰越 |
| [IT2 ふりかえり (KPT)](./development/retrospective-2.md) | Keep 9・Problem 11・Try 14 (必達 10 + 推奨 4)、ベロシティ実績 IT1 154% → IT2 100% |
| [IT3 計画](./development/iteration_plan-3.md) | US07 / US08a / US27 + IT2 繰越 + レビュー高優先 5 件、本体 11 + 拡張 29 SP |
| [IT3 完了報告書](./development/iteration_report-3.md) | 本体 3/3 + Try 4 + レビュー 5 + 横断 2 = 22 SP 完了、ストレッチ 7 SP は IT4 繰越、ADR 3 件起票 |
| [IT3 ふりかえり (KPT)](./development/retrospective-3.md) | post-nav レビュー H-01〜H-03 即時対応、ShipperRef 導入で arch-check Rule 4 ALLOWLIST 0 件化 |
| [IT4 計画](./development/iteration_plan-4.md) | US08b/US09/US11/US13 (経路評価→確定→紐付け→予約確定) + IT3 繰越 7 SP + 拡張 2 SP = 20 SP、Release 0.2 リリース |
| [IT4 完了報告書](./development/iteration_report-4.md) | 本体 4/4 ストーリー完了、19.0 SP (95%)、Ralph Loop 18 反復で消化 |
| [IT4 ふりかえり (KPT)](./development/retrospective-4.md) | IT4 の Keep/Problem/Try 抽出 |
| [IT5 計画](./development/iteration_plan-5.md) | Phase 3 前半 US14/15/16/18 + IT4 繰越 3 + Try 5 + 拡張 2 + 上流補完 2 = 22 SP |
| [IT5 完了報告書](./development/iteration_report-5.md) | 本体 4 + task 1.2/3.1-3.3 + Ralph 21 iter、40+ SP、新 BC 2 件 (Tracking/Handling)、ADR 3 件更新、E2E globalSetup 配線 |
| [IT5 ふりかえり (KPT)](./development/retrospective-5.md) | T5-01〜T5-21 の 21 アクション抽出 (高 10・中 15・低 7) |
| [IT6 計画](./development/iteration_plan-6.md) | US21 料金算出 + US26 引取通知 + IT5 繰越 8 (T5-01〜T5-10) + プロセス品質 3 + 上流補完 2 = 18 SP、Release 1.0 MVP 目標 |
| [IT6 完了報告書](./development/iteration_report-6.md) | Ralph Loop 30 反復で 30+ SP (計画 18 SP 対比 167%)、Pricing/Notification BC 新設、Postgres 実装まで一巡完成、+139 tests (641 total) |
| [IT6 ふりかえり (KPT)](./development/retrospective-6.md) | Keep 15 / Problem 10 / Try 12 (T6-01〜T6-12)、平均ベロシティ 24.8 SP、IT7 冒頭必達 4 件 (E2E / developing-review / v1.0.0-mvp tag / 上流ドキュメント同期) |
| [IT7 計画](./development/iteration_plan-7.md) | 例外処理 (US19/US20) + 手動状態更新 (US17) + 法人割引 (US22) + IT6 繰越 (T6-01〜T6-04) + プロセス品質 (T6-05〜T6-09) = 10 SP、Release 2.0 GA 橋渡し目標 |
| [IT7 完了報告書](./development/iteration_report-7.md) | Ralph Loop 2 週 66 反復で 30+ SP (計画 10 SP 対比 300%+)、Exception BC 新設 (17 モジュール)、ADR-0013/0014/0015 起票、+135 tests (776 total) |
| [IT7 ふりかえり (KPT)](./development/retrospective-7.md) | Keep 6 / Problem 4 / Try 14 (T7-A〜T7-N)、平均ベロシティ 25.6 SP、IT8 冒頭必達 4 件 (RolePolicy 配線 / hedgehog / UNLOAD 副作用テスト / ADR-0016) |
| [IT8 計画](./development/iteration_plan-8.md) | 精算処理 (US23) + IT7 繰越高優先 (T7-A〜T7-D) + 保証系 (T7-E〜T7-I) + Release 2.0 GA クロージング + ストレッチ (US10/US12) = 22 SP |
| [IT8 完了報告書](./development/iteration_report-8.md) | Release 2.0 GA (v2.0.0) 達成。US23 精算を Billing BC で全レイヤ一巡 + 保証系完済 (RoleGate/katip/E2E 統合ハッピーパス)、+113 tests (889 total)、実績 21+ SP / 98% |
| [IT8 ふりかえり (KPT)](./development/retrospective-8.md) | Keep 9 / Problem 8 / Try (高 3 T8-A〜C + 中 5 + 低 5)、平均ベロシティ 25.0 SP、IT9 は残務回収 IT (H-01 Tx 配線 / H-02 OverdueCheck 起動主体 / 料金実値化) |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー (高 11・中 12・低 5) |
| [分析整合性セルフレビュー](./review/analysis_consistency_review_20260626.md) | grep ベース機械的整合性検証 (集約↔テーブル、UC↔US 等) |
| [分析多視点レビュー](./review/analysis_multi_perspective_review_20260626.md) | XP 5 エージェント並列レビュー (高 16・中 16・低 10) |
| [IT8 開発マルチパースペクティブレビュー](./review/it8_review_20260707.md) | US23 精算 + 保証系の XP 5 エージェント並列レビュー。即対応 5 件 (H-03〜05/M-01/02) + IT9 backlog 3 件 (H-01 Tx/H-02 OverdueCheck/H-06 E2E) |
| [IT1 コードレビュー](./review/it1_code_review_20260626.md) | IT1 全体の XP 5 エージェント並列レビュー (高 10・中 13・低 8) |
| [IT2 コードレビュー](./review/it2_code_review_20260627.md) | IT2 全体の XP 5 エージェント並列レビュー (高 12・中 14・低 12、矛盾 3) |
| [IT3 post-nav レビュー](./review/it3_post_nav_review_20260629.md) | IT3 完了時 nav/E2E レビュー |
| [IT4 コードレビュー](./review/it4_code_review_20260630.md) | IT4 XP 5 エージェント並列レビュー |
| [IT5 コードレビュー](./review/it5_code_review_20260701.md) | IT5 全体の XP 5 エージェント並列レビュー |
| [IT6 nav/E2E レビュー](./review/it6_nav_e2e_review_20260702.md) | IT6 完了時 nav/E2E 高優先指摘反映 (H-01) |
| [IT7 Ralph Loop 2 週目レビュー](./review/ralph-loop-week2_review_20260703.md) | Ralph 2 週目 iter 1-8 XP 5 エージェント並列レビュー (高 4・中 5・低 5、矛盾 0) |

## 補足

- `strategy/`、`requirements/`、`design/`、`development/`、`operation/` は現時点ではカテゴリ索引が中心です。
- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
