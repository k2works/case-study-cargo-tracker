# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 | 状況 |
|-------------|------|------|
| [リリース計画](release_plan.md) | 8 イテレーション・76 SP・Axon Kafka + Heroku 構成 | 作成済み |

### イテレーション計画

| イテレーション | 期間 | 計画 | ふりかえり | 完了報告書 | 状態 |
|---------------|------|------|-----------|-----------|------|
| IT1 | 2026-05-21 〜 2026-06-03 | [iteration_plan-1.md](iteration_plan-1.md) | [retrospective-1.md](retrospective-1.md) | [iteration_report-1.md](iteration_report-1.md) | 完了 |
| IT2 | 2026-06-04 〜 2026-06-17 | [iteration_plan-2.md](iteration_plan-2.md) | [retrospective-2.md](retrospective-2.md) | [iteration_report-2.md](iteration_report-2.md) | 完了 |
| IT3 | 2026-06-18 〜 2026-07-01 | [iteration_plan-3.md](iteration_plan-3.md) | [retrospective-3.md](retrospective-3.md) | [iteration_report-3.md](iteration_report-3.md) | 完了 |
| IT4 | 2026-07-02 〜 2026-07-15 | [iteration_plan-4.md](iteration_plan-4.md) | [retrospective-4.md](retrospective-4.md) | [iteration_report-4.md](iteration_report-4.md) | 完了（Release 1.0 MVP）|
| IT5 | 2026-07-16 〜 2026-07-29 | [iteration_plan-5.md](iteration_plan-5.md) | [retrospective-5.md](retrospective-5.md) | [iteration_report-5.md](iteration_report-5.md) | 完了 |
| IT6 | 2026-07-30 〜 2026-08-12 | [iteration_plan-6.md](iteration_plan-6.md) | [retrospective-6.md](retrospective-6.md) | [iteration_report-6.md](iteration_report-6.md) | 完了（Release 2.0）|
| IT7 | 2026-08-13 〜 2026-08-26 | [iteration_plan-7.md](iteration_plan-7.md) | [retrospective-7.md](retrospective-7.md) | [iteration_report-7.md](iteration_report-7.md) | 完了（Release 2.1、billingms 新設・精算）|
| IT8 | 2026-08-27 〜 2026-09-09 | [iteration_plan-8.md](iteration_plan-8.md) | - | [iteration_report-8.md](iteration_report-8.md) | 完了（Release 1.0 候補、A1 ShedLock + A2 SendGrid + A3 RestShipperInfoAcl + A4 PaymentDetailRecorded + ADR-0020 + H2 持ち越し全 8 件 + ADR-0021）|
| IT9 | 2026-09-10 〜 2026-09-23 | [iteration_plan-9.md](iteration_plan-9.md) | [retrospective-9.md](retrospective-9.md) | [iteration_report-9.md](iteration_report-9.md) | 完了（Release 1.1 主要機能完全実装、Stripe webhook + AWS Secrets Manager + 認可基盤 + SendGrid WireMock、**8/8 SP**、IT8 review 11 件全解消）|
| IT10 | 2026-06-08 〜 2026-06-19 | [iteration_plan-10.md](iteration_plan-10.md) | - | -（[journal-it10.md](journal-it10.md) 中間サマリ） | 進行中（Release 1.1 正式版昇格、A1 認可深層強化 + A2 fallback UX 改善 + A4 Flyway × enum 同期検証 完遂、A3 staging + A5 残作業は staging 実機環境構築フェーズ、**5/8 SP 達成・62.5%**、IT9 review 12 件中 9 件解消）|
| IT11 | IT10 完了直後（staging 構築完了に依存、仮 2026-06-22〜2026-07-03）| [iteration_plan-11.md](iteration_plan-11.md)（スケルトン） | - | - | 未着手（Release 1.2 着手 / 構造的負債返済 + 業務スコープ拡張：B1 共通化リファクタリング + B2 SDK contract test + B3 Prometheus alert rule + B4/B5 US28/US29 候補スコープ確定、IT10 中間レビュー H/M 持ち越し + 業務拡張）|

### Release 1.0 完了報告書（暫定）

- [release_report-1.0.md](release_report-1.0.md) — Release 1.0 候補確立報告書（IT8 完全達成、76/76 SP、ADR-0020/0021 起票）

### Release 1.1 完了報告書（仮版）

- [release_report-1_1_0.md](release_report-1_1_0.md) — Release 1.1 仮版報告書（IT9 + IT10 AI 単独完結部分、89/92 SP・97% 達成、staging 残 3 SP は実機検証完了後に正式版へ昇格）

### 進捗サマリー

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|---------------|---------|---------|--------|
| IT1 | 10 | 10 | 100% |
| IT2 | 10 | 10 | 100% |
| IT3 | 10 | 10 | 100% |
| IT4 | 11 | 11 | 100% |
| IT5 | 10 | 10 | 100% |
| IT6 | 9 | 9 | 100% |
| IT7 | 8 | 8 | 100% |
| IT8 | 8 | 8 | 100% |
| IT9 | 8 | 8 | 100% |
| IT10 | 8 | 5 | 62.5%（AI 単独完結部分、残 3 SP は staging 実機環境）|
| **累計** | **92** | **89** | **96.7%** |

### フェーズ進捗

| フェーズ | 内容 | SP | 完了 SP | 状態 |
|---------|------|-----|---------|------|
| Phase 1 | 基盤・認証・予約・経路設計（IT1-IT4） | 41 | 41 | 完了（Release 1.0 MVP）|
| Phase 2 | 追跡・例外処理・精算（IT5-IT7） | 27 | 27 | 完了（IT5/IT6/IT7 完了、Release 2.1）|
| Buffer | 非機能・品質改善・本番デプロイ準備（IT8）| 8 | 8 | 完了（Release 1.0 候補確立）|

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|
| Release 1.0 MVP | 未作成 | 未着手 |
| Release 2.0 | 未作成 | 未着手 |
| Release 2.1 | 未作成 | 未着手 |

## 補足

- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
