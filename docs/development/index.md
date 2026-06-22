# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画](./release_plan.md) | リリース全体のスコープ（26 US / 91 SP / 9 IT / 4 リリース）、ベロシティ、バッファ戦略 |

### イテレーション計画

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|-----------|-----------|------|
| IT1（2026-06-22 〜 2026-07-05） | [iteration_plan-1.md](./iteration_plan-1.md) | [retrospective-1.md](./retrospective-1.md) | [iteration_report-1.md](./iteration_report-1.md) | ✅ 完了（12/12 SP） |
| IT2（2026-07-06 〜 2026-07-19） | [iteration_plan-2.md](./iteration_plan-2.md) | [retrospective-2.md](./retrospective-2.md) | [iteration_report-2.md](./iteration_report-2.md) | ✅ 完了（12/12 SP、v0.1.0 タグ付けはユーザー判断委譲） |
| IT3（2026-07-20 〜 2026-08-02） | [iteration_plan-3.md](./iteration_plan-3.md) | [retrospective-3.md](./retrospective-3.md) | [iteration_report-3.md](./iteration_report-3.md) | ✅ 完了（11/11 SP / US07 + US08 + IT2 申し送り 15 件、テスト 224 / coverage 88.0%） |
| IT4（2026-08-03 〜 2026-08-16） | [iteration_plan-4.md](./iteration_plan-4.md) | [retrospective-4.md](./retrospective-4.md) | [iteration_report-4.md](./iteration_report-4.md) | ✅ 完了（11/11 SP / US09 + US11 + US12 + US13 + IT3 申し送り 9 件、テスト 288 / coverage 88.21%） |
| IT5（2026-08-17 〜 2026-08-30） | [iteration_plan-5.md](./iteration_plan-5.md) | - | - | 🚧 計画中（11 SP 目標 / US14 + US15 + US18 + IT4 申し送り 6 件、Tracking Context 新設） |

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 12 | 12 | 100% |
| IT2 | 12 | 12 | 100% |
| IT3 | 11 | 11 | 100% |
| IT4 | 11 | 11 | 100% |
| **累計** | **46** | **46** | **100%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1 | 認証 + 予約・荷主基盤 + 航海スケジュール | 22 | 24 | ✅ 完了（IT1 + IT2、Release 0.1 Internal Alpha） |
| Phase 2 | 経路設計・確定 | 22 | 22 | ✅ 完了（IT3 + IT4） |
| Phase 3 | 追跡・状態更新 + 料金算出 | 23 | 0 | 未着手 |
| Phase 4 | 例外処理・割引・精算 | 21 | 0 | 未着手 |
| 予備 | バッファ消費・US10 | 3 | 0 | 未着手 |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|
| Release 0.1 Internal Alpha | [release-0.1.0-gate-check.md](./release-0.1.0-gate-check.md) | ✅ ゲート確認済（v0.1.0 タグ付け待ち） |
| Release 0.2 | - | 未リリース |
| Release 1.0 MVP | - | 未リリース |
| Release 2.0 GA | - | 未リリース |

## 補足

- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
