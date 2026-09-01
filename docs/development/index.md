# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](release_plan.md) | 6 リリース・33US・110SP。ベロシティ 8〜10SP/IT・バッファ戦略 |
| [開発戦略](development_strategy.md) | IT1〜12 + 予備 IT13 を序盤・中盤・終盤の 3 局面に分け、局面別 TDD アプローチ（アウトサイドイン/インサイドアウト）を定義 |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1 | [計画](iteration_plan-1.md) | [ふりかえり](retrospective-1.md) | [完了報告書](iteration_report-1.md) | 完了 |
| IT2 | [計画](iteration_plan-2.md) | [ふりかえり](retrospective-2.md) | [完了報告書](iteration_report-2.md) | 完了 |
| IT3 | [計画](iteration_plan-3.md) | [ふりかえり](retrospective-3.md) | [完了報告書](iteration_report-3.md) | 完了 |
| IT4 | [計画](iteration_plan-4.md) | [ふりかえり](retrospective-4.md) | [完了報告書](iteration_report-4.md) | 完了 |
| IT5 | [計画](iteration_plan-5.md) | [ふりかえり](retrospective-5.md) | [完了報告書](iteration_report-5.md) | 完了 |
| IT6 | [計画](iteration_plan-6.md) | [ふりかえり](retrospective-6.md) | [完了報告書](iteration_report-6.md) | 完了 |
| IT7 | [計画](iteration_plan-7.md) | [ふりかえり](retrospective-7.md) | [完了報告書](iteration_report-7.md) | 完了 |
| IT8 | [計画](iteration_plan-8.md) | [ふりかえり](retrospective-8.md) | [報告書](iteration_report-8.md) | 完了 |
| IT9 | [計画](iteration_plan-9.md) | [ふりかえり](retrospective-9.md) | [完了報告書](iteration_report-9.md) | 完了 |
| IT10 | [計画](iteration_plan-10.md) | [ふりかえり](retrospective-10.md) | [完了報告書](iteration_report-10.md) | 完了 |
| IT11 | [計画](iteration_plan-11.md) | [ふりかえり](retrospective-11.md) | [完了報告書](iteration_report-11.md) | 完了 |
| IT12 | [計画](iteration_plan-12.md) | [ふりかえり](retrospective-12.md) | [完了報告書](iteration_report-12.md) | 完了 |
| IT13 | [計画](iteration_plan-13.md) | [ふりかえり](retrospective-13.md) | [完了報告書](iteration_report-13.md) | 完了 |
| IT14 | [計画](iteration_plan-14.md) | [ふりかえり](retrospective-14.md) | [完了報告書](iteration_report-14.md) | 完了 |
| IT15 | [計画](iteration_plan-15.md) | [ふりかえり](retrospective-15.md) | [完了報告書](iteration_report-15.md) | 完了 |
| IT16 | [計画](iteration_plan-16.md) | - | - | 計画済み |

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 9 | 9 | 100% |
| IT2 | 8 | 8 | 100% |
| IT3 | 10 | 10 | 100% |
| IT4 | 8 | 8 | 100% |
| IT5 | 8 | 8 | 100% |
| IT6 | 9 | 9 | 100% |
| IT7 | 10 | 10 | 100% |
| IT8 | 9 | 9 | 100% |
| IT9 | 10 | 10 | 100% |
| IT10 | 7 | 7 | 100% |
| IT11 | 9 | 9 | 100% |
| IT12 | 11（うち 3 は US21 の再実施） | 11 | 100% |
| IT13 | 7（US33 5 + バッファ TD-01 2） | 7 | 100% |
| IT14 | 8（US34 5 + US35 3） | 8 | 100% |
| IT15 | 8（US36 3 + US37 5） | 8 | 100% |
| **累計（IT1〜IT13）** | **112**（33US 110 SP + TD-01 2 SP） | **112** | **100%** |
| **Release 2.2（IT14〜IT15）** | **16**（US34〜US37） | **16** | **100%** |

> **IT12 の 11 SP のうち 3 SP は US21（IT11）の再実施**です（距離・輸出免税）。累計にはリリース計画と同じく **8 SP** として算入しています（[release_plan.md](release_plan.md) 529 行）。

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|
| Release 1.0（IT7-8・19 SP） | [報告書](release_report-1_0_0.md) | 完了 |
| Release 1.1（IT9-10・17 SP） | [報告書](release_report-1_1_0.md) | 完了 |
| Release 2.0（IT11-12・17 SP） | [報告書](release_report-2_0_0.md) | 完了 |
| Release 2.1（IT13・5 SP + バッファ 2 SP） | [報告書](release_report-2_1_0.md) | 完了 |
| Release 2.2（IT14-15・16 SP） | - | 完了（`java/take-7/v2.2.0`） |

## 補足

- 現在はカテゴリ索引のみ存在します。
- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
