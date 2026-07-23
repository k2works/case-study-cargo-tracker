# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](release_plan.md) | リリース全体のスコープ、スケジュール、ベロシティ、バッファ戦略（26 US・97 SP・8 イテレーション・3 リリース） |
| [開発戦略](development_strategy.md) | イテレーションを 3 局面（序盤/中盤/終盤）に分け、局面別 TDD アプローチとウォーキングスケルトン・設計整合方針を定義 |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1 予約基盤 | [iteration_plan-1.md](iteration_plan-1.md) | [retrospective-1.md](retrospective-1.md) | [iteration_report-1.md](iteration_report-1.md) | 完了（機能スコープ・全テスト green） |
| IT2 航海スケジュール | [iteration_plan-2.md](iteration_plan-2.md) | [retrospective-2.md](retrospective-2.md) | [iteration_report-2.md](iteration_report-2.md) | 完了（US24・US25・US07・レビュー済・DIP 回復 ADR-0003） |
| IT3 経路算出・選択 | [iteration_plan-3.md](iteration_plan-3.md) | [retrospective-3.md](retrospective-3.md) | [iteration_report-3.md](iteration_report-3.md) | 完了（US08 経路候補算出・US09 経路選択確定・レビュー済・CI 品質ゲート導入） |
| IT4 経路連携・予約確定 | [iteration_plan-4.md](iteration_plan-4.md) | [retrospective-4.md](retrospective-4.md) | [iteration_report-4.md](iteration_report-4.md) | 完了（US06・US10・US11・US12・US13・レビュー済・ADR-0004/0005 起票） |
| IT5 追跡・荷役 | [iteration_plan-5.md](iteration_plan-5.md) | [retrospective-5.md](retrospective-5.md) | [iteration_report-5.md](iteration_report-5.md) | 完了（US14・US15・US16・US17・レビュー済・ADR-0006 起票・Release 1.0 MVP 完成） |
| IT6 見積・照会・遅延例外 | [iteration_plan-6.md](iteration_plan-6.md) | - | - | 計画済み（US01・US18・US19・終盤アウトサイドイン・Release 1.1 起点） |

イテレーション開始時に行を追加します。

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 16 | 16 | 100% |
| IT2 | 11 | 11 | 100% |
| IT3 | 11 | 11 | 100% |
| IT4 | 14 | 14 | 100% |
| IT5 | 14 | 14 | 100% |
| **累計** | **97** | **66** | **68%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1 | 予約基盤（IT1） | 16 | 16 | 完了 |
| Phase 2 | コア輸送フロー（IT2-5） | 50 | 50 | 完了（IT2・IT3・IT4・IT5・Release 1.0 MVP 完成） |
| Phase 3 | 見積・例外対応・精算（IT6-8） | 31 | 0 | 未着手 |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|

## 補足

- 現在はカテゴリ索引のみ存在します。
- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
