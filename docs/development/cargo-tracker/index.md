# cargo-tracker — 開発

cargo-tracker プロジェクトの開発ドキュメントです。

## ドキュメント一覧

### 計画・戦略

| ドキュメント | 概要 |
| :--- | :--- |
| [リリース計画](release_plan.md) | US01〜US31 を 15 イテレーション・5 リリースに配分。SP・ベロシティ・バッファ・リスク |
| [開発戦略](development_strategy.md) | 序盤（IT1-3 アウトサイドイン）・中盤（IT4-10 インサイドアウト）・終盤（IT11-15 アウトサイドイン）の局面とアプローチ |
| [リリース完了報告書 0.1](release_report-0_1_0.md) | Release 0.1 予約基盤（IT1〜IT3・27 SP・達成率 100%） |

### イテレーション

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
| :--- | :--- | :--- | :--- | :--- |
| IT1 基盤・認証・荷主登録 | [計画](iteration_plan-1.md) | [ふりかえり](retrospective-1.md) | [完了報告書](iteration_report-1.md) | **完了**（実績 9 SP・達成率 100%。持ち越し 5 件） |
| IT2 貨物予約・法人荷主・アカウント保護 | [計画](iteration_plan-2.md) | [ふりかえり](retrospective-2.md) | [完了報告書](iteration_report-2.md) | **完了**（実績 9 SP・達成率 100%。欠陥 12 件を発見・修正） |
| IT3 危険物・引き渡し・航海登録 | [計画](iteration_plan-3.md) | [ふりかえり](retrospective-3.md) | [完了報告書](iteration_report-3.md) | **完了**（実績 9 SP・達成率 100%。欠陥 34 件を発見・修正。routingms を立ち上げ） |
| IT4 航海の更新と検索・予約の修正 | [計画](iteration_plan-4.md) | [ふりかえり](retrospective-4.md) | [完了報告書](iteration_report-4.md) | **完了**（実績 8 SP・達成率 100%。返済枠 6 件を繰越ゼロで返済。中盤の最初） |
| IT5 経路候補の算出と確定 | [計画](iteration_plan-5.md) | — | — | **開発中**（US08 6・US09 4 = 10 SP。引き継ぎ枠 6 件は繰越ゼロで返済。契約クエリ 1 本目が往復した） |
| IT6〜IT15 | 未作成 | — | — | 未着手 |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
