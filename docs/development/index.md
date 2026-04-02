# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](./release_plan.md) | リリース全体のスコープ、スケジュール、ベロシティ、バッファ戦略（6 イテレーション・64 SP） |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1（US02・03・04, 10SP） | [iteration_plan-1.md](./iteration_plan-1.md) | [retrospective-1.md](./retrospective-1.md) | [iteration_report-1.md](./iteration_report-1.md) | 完了 ✅ |
| IT2（US01・06, 10SP） | [iteration_plan-2.md](./iteration_plan-2.md) | [retrospective-2.md](./retrospective-2.md) | [iteration_report-2.md](./iteration_report-2.md) | 完了 ✅ |
| IT3（US05・07・08・09, 12SP） | [iteration_plan-3.md](./iteration_plan-3.md) | [retrospective-3.md](./retrospective-3.md) | [iteration_report-3.md](./iteration_report-3.md) | 完了 ✅ |
| IT4（US10・11・12・13, 13SP） | [iteration_plan-4.md](./iteration_plan-4.md) | — | — | 進行中 🚧 |

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 10 | 10 | 100% |
| IT2 | 10 | 10 | 100% |
| IT3 | 12 | 12 | 100% |
| **累計** | **64** | **32** | **50%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1 | コア輸送管理（US01〜US15） | 51 | 32 | 進行中 |
| Phase 2 | 請求・精算（US16〜US18） | 13 | 0 | 未着手 |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|

## 補足

- リリース計画は [release_plan.md](./release_plan.md) を参照してください。
- IT1 完了後に、E2E 強化、荷主 / 予約の Web・REST 分離、Swagger UI、default seed data、レビュー指摘反映まで実施済みです。
- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
