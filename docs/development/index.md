# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](./release_plan.md) | リリース全体のスコープ、スケジュール、ベロシティ、バッファ戦略（27 US・85 SP・7+1 IT） |
| [開発戦略](./development_strategy.md) | イテレーションを 3 局面（序盤/中盤/終盤）に分け、各局面の TDD アプローチと不変規律を定義 |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1 基盤 + 荷主・見積 | [iteration_plan-1.md](./iteration_plan-1.md) | [retrospective-1.md](./retrospective-1.md) | - | 開発完了 |
| IT2 貨物予約 | [iteration_plan-2.md](./iteration_plan-2.md) | [retrospective-2.md](./retrospective-2.md) | [iteration_report-2.md](./iteration_report-2.md) | 開発完了 |
| IT3 航海・経路算出 | [iteration_plan-3.md](./iteration_plan-3.md) | [retrospective-3.md](./retrospective-3.md) | [iteration_report-3.md](./iteration_report-3.md) | 開発完了 |
| IT4 経路確定・予約確定 | [iteration_plan-4.md](./iteration_plan-4.md) | [retrospective-4.md](./retrospective-4.md) | [iteration_report-4.md](./iteration_report-4.md) | 開発完了 |
| IT5 追跡・荷役 | [iteration_plan-5.md](./iteration_plan-5.md) | [retrospective-5.md](./retrospective-5.md) | [iteration_report-5.md](./iteration_report-5.md) | 開発完了 |
| IT6 例外対応 | [iteration_plan-6.md](./iteration_plan-6.md) | [retrospective-6.md](./retrospective-6.md) | [iteration_report-6.md](./iteration_report-6.md) | 開発完了 |
| IT7 割引・請求・精算 | [iteration_plan-7.md](./iteration_plan-7.md) | - | - | 計画済み |

イテレーション開始時に行を追加します。

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 10 | 10 | 100% |
| IT2 | 10 | 10 | 100% |
| IT3 | 14 | 14 | 100% |
| IT4 | 12 | 12 | 100% |
| IT5 | 17 | 17 | 100% |
| **累計** | **63** | **63** | **100%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1（Release 1.0 / MVP） | 荷主・見積・予約・航海/経路・追跡・荷役（IT1-5） | 63 | 0 | 未着手 |
| Phase 2（Release 1.1） | 例外対応・請求・精算（IT6-7） | 22 | 0 | 未着手 |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|

## 補足

- 現在はカテゴリ索引のみ存在します。
- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
