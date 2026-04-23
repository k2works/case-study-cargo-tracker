# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](./release_plan.md) | リリース全体のスコープ、スケジュール、ベロシティ、バッファ戦略 |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1 | [計画](./iteration_plan-1.md) | [ふりかえり](./retrospective-1.md) | [報告書](./iteration_report-1.md) | 完了 |
| IT2 | [計画](./iteration_plan-2.md) | [ふりかえり](./retrospective-2.md) | [報告書](./iteration_report-2.md) | 完了（Java 166 件・E2E 31 件全パス、カバレッジ 93%/81%） |
| IT3 | [計画](./iteration_plan-3.md) | [ふりかえり](./retrospective-3.md) | [報告書](./iteration_report-3.md) | 完了（約 184 件・E2E 41 件全パス） |
| IT4 | [計画](./iteration_plan-4.md) | [ふりかえり](./retrospective-4.md) | [報告書](./iteration_report-4.md) | 完了（190 件・E2E 40 件全パス、カバレッジ 91%/75%、US07・US08 完了） |
| IT5 | [計画](./iteration_plan-5.md) | [ふりかえり](./retrospective-5.md) | [報告書](./iteration_report-5.md) | 完了（Java 250 件・E2E 56 件全パス、カバレッジ 88%/75%、US09・US10・US11 完了） |
| IT6 | [計画](./iteration_plan-6.md) | [ふりかえり](./retrospective-6.md) | [報告書](./iteration_report-6.md) | 完了（Java 272 件・E2E 67 件全パス、カバレッジ 81%、US22・US23 完了） |
| IT7 | [計画](./iteration_plan-7.md) | [ふりかえり](./retrospective-7.md) | [報告書](./iteration_report-7.md) | 完了（Java テスト全パス・E2E 78 件・カバレッジ 81.7%・US14・US15 完了） |
| IT8 | [計画](./iteration_plan-8.md) | [ふりかえり](./retrospective-8.md) | [報告書](./iteration_report-8.md) | 完了（Java 301 件・E2E 87 件全パス・IT7-改善・US16・US17・US18 完了） |
| IT9 | [計画](./iteration_plan-9.md) | [ふりかえり](./retrospective-9.md) | [報告書](./iteration_report-9.md) | 完了（Java 315 件・E2E 93 件全パス・カバレッジ 80%・SonarQube PASS・US19・US20 完了） |
| IT10 | [計画](./iteration_plan-10.md) | - | [報告書](./iteration_report-10.md) | 完了（Java 323 件・E2E 98 件全パス・カバレッジ 80.9%・SonarQube PASS・IT9-改善・US21 完了・Release 2.0 完成） |

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 10 | 10 | 100% |
| IT2 | 10 | 10 | 100% |
| IT3 | 10 | 10 | 100% |
| IT4 | 10 | 8 | 80%（SonarQube 保留） |
| IT5 | 10 | 10 | 100% |
| IT6 | 10 | 10 | 100% |
| IT7 | 10 | 10 | 100% |
| IT8 | 10 | 10 | 100% |
| IT9 | 12 | 12 | 100% |
| IT10 | 8 | 8 | 100% |
| **累計** | **100** | **98** | **98%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1 | 予約・荷主管理基盤 | 16 | 16 | 完了（IT1-2 で全 US 完了） |
| Phase 2 | 経路設計・追跡 | 44 | 44 | 完了（IT3-5: US01・US06-US11 完了、IT7: US14・US15 完了、IT8: US16・US17・US18 完了） |
| Phase 3 | 精算・例外処理 | 26 | 26 | 完了（IT6: US22・US23 完了、IT9: US19・US20 完了、IT10: US21 完了） |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|
| v1.0.0 | [リリース完了報告書](./release_report-1_0_0.md) | 完了（Java 272 件・E2E 67 件・カバレッジ 81%） |
| v2.0.0 | - | Release 2.0 完成（Java 323 件・E2E 98 件・カバレッジ 80.9%・SonarQube PASS） |

## 補足

- 現在はカテゴリ索引のみ存在します。
- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
