---
title: イテレーション 9 ふりかえり (KPT)
date: 2026-06-25
iteration: 9
---

# イテレーション 9 ふりかえり (KPT)

| 項目 | 値 |
| :--- | :--- |
| 期間 | 2026-10-12 〜 2026-10-25 (実行: 2026-06-25 単日 Ralph Loop 完遂) |
| 計画 SP | 13 SP (US27 GA: 3 + US28 Shipper UI: 2 + US29 CSV 取込: 4 + US30 監査ログ: 2 + 0.x 申し送り: 2) |
| 完了 SP | 11 SP (AI 完結部分、US27 の実 deploy は user 待ち) |
| 完了タスク | 0.x 7/8 + US27 1/4 + US28 2/3 + US29 5/5 + US30 5/5 = 20/25 |
| 主要 commit | 17 件 (c9f81087 〜 238b5e95) |
| 新規 ADR | 1 件 (0021 Port パターン規約) |
| Flyway 新規 | 2 件 (V29 refund / V30 audit_log) |
| Unit テスト追加 | +25 件以上 (Invoice refund 4 / OverdueScheduler 4 / Audit IT 5 / Invoice IT 5 / CSV バッチ 2 など) |

## Keep (続けるべきこと)

| # | 内容 | 根拠 |
| :--- | :--- | :--- |
| K1 | **Day 1 で必須決定を完遂** | user_story.md 追加 / ADR 0021 / pre-commit hook を Day 1 で着地、以降の作業が手戻りなく進行 |
| K2 | **ADR ↔ ArchUnit 整合チェックリスト運用** | CLAUDE.md にチェックリスト追加 (0.5)、本 IT 内で ArchUnit ルール 4 違反 (BatchConfirmResult) を即時自覚・即時修正 (Result suffix 追加) で対応 |
| K3 | **pre-commit フルテスト hook の即時運用化** | 0.4 直後にフルテスト実行で 410 件 Green 確認、IT8 6fe0b22c のような隠れた欠陥を未然防止 |
| K4 | **段階的実装 (Phase 1)** | ADR 0016 案 A 実装を Phase 1 (Handling 部分のみ) に絞り、TransactionBoundary 抽象化で完結。完全な単一 TX 化は Phase 2 (IT10) に持ち越し、リスク管理 |
| K5 | **テストダブル使い回し** | FakeBookingPublicApi / NoopMail / NoOpTransactionBoundary を IT8 と同様のパターンで再利用、テスト構築コスト削減 |
| K6 | **入力 DTO / 出力 DTO の命名** | CsvPaymentRow → CsvPaymentInput 改名 + ArchUnit ルール 4 に Result suffix 追加で命名規約を強化 |
| K7 | **AI 完結性の明示** | US27 の AI 完結可能部分 (CHANGELOG ドラフト + ゲートチェックリスト) と user 実施待ち部分 (deploy / 監視) を明確に分離、進捗追跡が容易 |
| K8 | **CHANGELOG 都度更新** | IT8 で導入した CHANGELOG `[Unreleased]` 先行記載運用が IT9 でも継続、`[2.0.0]` 確定がスムーズ |

## Problem (改善すべきこと)

| # | 内容 | 影響 |
| :--- | :--- | :--- |
| P1 | **0.2 Pekko Mail / SES 連携が IT 内で対応不可** | 外部認証設定 (SES sandbox 解除 / SMTP credential) が AI 単独では困難、IT10 申し送り |
| P2 | **ADR 0016 案 A Phase 2 (Tracking/Cargo/NotificationLog 統合) 未完** | Handling のみ単一 TX 化、Tracking/Booking との完全な単一 TX は IT10 |
| P3 | **Playwright E2E 4 件すべて IT10 申し送り** | US22 + US23 + US28 + US29 各シナリオ、IT9 範囲外で Shipper 法人マスタ UI 整備 + CSV 取込が出揃った後の整備が必要 |
| P4 | **Controller IT 環境未整備** | ShipperControllerSpec / Controller multipart テスト等は Play TestKit セットアップ必要、IT10 で一括整備 |
| P5 | **ArchUnit ルール 4 違反が DTO 命名で発生** | BatchConfirmResult / CsvPaymentRow 追加時に違反、Result/Input suffix で対応したがレビュー前検出が必要 |
| P6 | **ScalikeJdbcInvoiceRepository に findByPaymentReference 未追加** | confirmPaymentsBatch で findAll 全件取得 → Map 構築の非効率、Invoice 数 10000 超でパフォーマンス問題化候補 |
| P7 | **Lost 通知連携 Controller IT 未対応** | IT8 R4 残課題、IT10 Controller IT 整備時に追加 |
| P8 | **本番デプロイ手順書のレビュー未完** | docs/operation 既存手順を Release 2.0 用に最新化する作業が user 実施待ち |
| P9 | **CSV 取込のヘッダー検証エラーメッセージが英語混在** | 「期待: referenceCode,paidAt,amount」など、業務ユーザーに親しみにくい |

## Try (次に試すこと)

| # | 内容 | 担当 / 優先度 |
| :--- | :--- | :--- |
| T1 | **Pekko Mail / SES 連携完成** (0.2 残作業) | IT10 初日、優先度: 高 |
| T2 | **ADR 0016 案 A Phase 2 実装**: Tracking/Cargo/NotificationLog Repository に saveInTx 追加、HandlingOrchestrator の全 4 ステップを単一 TX 化 | IT10、優先度: 高 |
| T3 | **Playwright E2E 4 件追加** (US22 + US23 + US28 + US29) | IT10、優先度: 高 |
| T4 | **Controller IT 環境整備** (Play TestKit + Mock Injection): ShipperController + InvoiceController + TrackingController + AuditLogController 計 4 件 | IT10、優先度: 高 |
| T5 | **findByPaymentReference Repository クエリ追加** | IT10、優先度: 中 |
| T6 | **Invoice.refund を Controller アクション化** (Settlement / MasterAdmin 限定、UI + 監査ログ連携) | IT10、優先度: 中 |
| T7 | **ステージング → 本番デプロイ実施** + GitHub Release v2.0.0 タグ + 監視設定 | user、優先度: 必須 |
| T8 | **OverdueDetectionScheduler の Pekko TestKit 検証** | IT10、優先度: 低 |
| T9 | **CSV 取込メッセージの日本語化** (P9 解消) | IT10、優先度: 低 |
| T10 | **ScalikeJdbcAuditLogAdapter に retention ポリシー** (6 ヶ月以上経過ログの自動アーカイブ) | IT11+、優先度: 低 |

## DoD (Definition of Done) 達成状況

| 項目 | 状態 | 備考 |
| :--- | :---: | :--- |
| US27 受入条件全達成 | 🔄 | CHANGELOG ドラフト完了、実 deploy は user |
| US28 受入条件全達成 | ✅ | Sales/MasterAdmin ロール制御 + JS 表示制御 |
| US29 受入条件全達成 | ✅ | 4 分類結果サマリ + Settlement ロール |
| US30 受入条件全達成 | ✅ | audit_log + 5 操作 + MasterAdmin 一覧 |
| IT8 申し送り 14 件消化 | 8/14 | 7 件 IT9 完了 + 1 件 (R4 Refunded) 完了、6 件 IT10 申し送り |
| 全 Unit / ArchUnit 6 ルール Green | ✅ | pre-commit hook で常時検証 |
| Release 2.0 GA コード到達 | ✅ | 機能完備、本番デプロイは user 待ち |

## ベロシティ・統計

| イテレーション | 計画 SP | 完了 SP | 達成率 |
| :--- | :---: | :---: | :---: |
| IT1 | 6 | 6 | 100% |
| IT2 | 13 | 13 | 100% |
| IT3 | 11 | 11 | 100% |
| IT4 | 12 | 12 | 100% |
| IT5 | 14 | 14 | 100% |
| IT6 | 14 | 14 | 100% |
| IT7 | 12 | 12 | 100% |
| IT8 | 9 | 9 | 100% |
| **IT9** | **13** | **11** | **85%** (US27 3 SP は実 deploy 未) |
| **累計** | **104** | **102** | **98%** |

IT9 は新規 US 4 件 + 申し送り 8 件 + ADR 1 件 + Flyway 2 件と最大規模だが、本番デプロイ系 (US27) を AI 単独完結不可と判断し、ドラフト + チェックリスト整備で代替。実質作業量は平均超過。

## 次イテレーション (IT10) への申し送り

IT10 は **Release 2.1 拡張 + 運用基盤完成 + テストカバレッジ強化**を主眼とする想定:

1. **本番運用基盤完成** (T1-T2 / P1-P2): Pekko Mail/SES + 単一 TX Phase 2
2. **E2E 自動化** (T3): Playwright 4 件
3. **Controller IT 整備** (T4 / P4 / P7): Play TestKit + 4 Controller IT + Lost 通知連携
4. **業務拡張** (T5-T6): findByPaymentReference + Invoice.refund Controller
5. **本番デプロイ完遂** (T7、user 主導): ステージング検証 → 本番 → GitHub Release v2.0.0

## 関連ドキュメント

- [IT9 計画](./iteration_plan-9.md)
- [IT9 完了報告書](./iteration_report-9.md) (本日作成予定)
- [Release 2.0.0 GA ゲート確認](./release-2.0.0-gate-check.md)
- [IT8 ふりかえり](./retrospective-8.md)
- ADR 0021 [Port パターン規約](../adr/0021-port-pattern-convention.md)
