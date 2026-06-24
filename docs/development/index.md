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
| IT5（2026-08-17 〜 2026-08-30） | [iteration_plan-5.md](./iteration_plan-5.md) | [retrospective-5.md](./retrospective-5.md) | [iteration_report-5.md](./iteration_report-5.md) | ✅ 完了（11/11 SP / US14 + US15 + US18 + IT4 申し送り 6 件、Tracking/Handling Context 新設、テスト 323 件） |
| IT6（2026-08-31 〜 2026-09-13） | [iteration_plan-6.md](./iteration_plan-6.md) | [retrospective-6.md](./retrospective-6.md) | [iteration_report-6.md](./iteration_report-6.md) | ✅ 完了（12/12 SP / US16 + US17 + US21 + IT5 申し送り 7/10 件、Billing Context 新設、Unit 261 + Playwright E2E 36/36 PASS、Release 1.0 MVP 機能完了） |
| IT7（2026-09-14 〜 2026-09-27） | [iteration_plan-7.md](./iteration_plan-7.md) | [retrospective-7.md](./retrospective-7.md) | [iteration_report-7.md](./iteration_report-7.md) | ✅ 完了（12/12 SP / US19 + US20 + IT6 申し送り 16/16 件、Flyway V18-V22 + ADR 0014/0015 承認、Unit 371 件、Phase 4 着手） |
| IT8（2026-09-28 〜 2026-10-11） | [iteration_plan-8.md](./iteration_plan-8.md) | [retrospective-8.md](./retrospective-8.md) | [iteration_report-8.md](./iteration_report-8.md) | ✅ 完了（9/9 SP / US22 + US23 + IT7 申し送り 15/15 件、ADR 0016-0020 承認、Flyway V23/V26/V27/V28、Unit +37 件、Phase 4 完了、Release 2.0 GA コード到達） |
| IT9（2026-10-12 〜 2026-10-25） | [iteration_plan-9.md](./iteration_plan-9.md) | [retrospective-9.md](./retrospective-9.md) | [iteration_report-9.md](./iteration_report-9.md) | ✅ 完了 (11/13 SP、US27 実 deploy は user 待ち / ADR 0021 + Flyway V29/V30 + TransactionBoundary + OverdueScheduler + AuditLog + Refund + CSV 取込 + 法人 Shipper UI + IT8 申し送り 8 件解消) |

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 12 | 12 | 100% |
| IT2 | 12 | 12 | 100% |
| IT3 | 11 | 11 | 100% |
| IT4 | 11 | 11 | 100% |
| IT5 | 11 | 11 | 100% |
| IT6 | 12 | 12 | 100% |
| IT7 | 12 | 12 | 100% |
| IT8 | 9 | 9 | 100% |
| IT9 | 13 | 11 | 85% (US27 実 deploy は user 待ち) |
| **累計** | **104** | **102** | **98%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1 | 認証 + 予約・荷主基盤 + 航海スケジュール | 22 | 24 | ✅ 完了（IT1 + IT2、Release 0.1 Internal Alpha） |
| Phase 2 | 経路設計・確定 | 22 | 22 | ✅ 完了（IT3 + IT4） |
| Phase 3 | 追跡・状態更新 + 料金算出 | 23 | 23 | ✅ 完了（IT5 + IT6、Release 1.0 MVP 機能完了） |
| Phase 4 | 例外処理・割引・精算 | 21 | 21 | ✅ 完了（IT7 で US19+US20、IT8 で US22+US23 + 申し送り 15 件、Release 2.0 GA コード到達） |
| 予備 | バッファ消費・US10 | 3 | 0 | 未着手 |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|
| Release 0.1 Internal Alpha | [release-0.1.0-gate-check.md](./release-0.1.0-gate-check.md) | ✅ ゲート確認済（v0.1.0 タグ付け待ち） |
| Release 0.2 | - | 未リリース |
| Release 1.0 MVP | [release_report-scala-1_0_0-mvp.md](./release_report-scala-1_0_0-mvp.md) | ✅ 機能完了（IT5+IT6 = 23 SP、v1.0.0 タグ付けはユーザー判断委譲） |
| Release 2.0 GA | [release_report-scala-2_0_0-ga.md](./release_report-scala-2_0_0-ga.md) | ✅ コード完了（IT7+IT8+IT9 = 32/34 SP、本番 deploy + v2.0.0 タグ push は user 待ち） |

## 補足

- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
