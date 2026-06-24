---
title: イテレーション 8 ふりかえり (KPT)
date: 2026-06-24
iteration: 8
---

# イテレーション 8 ふりかえり (KPT)

| 項目 | 値 |
| :--- | :--- |
| 期間 | 2026-09-28 〜 2026-10-11 (実行: 2026-06-24 単日 Ralph Loop 完遂) |
| 計画 SP | 9 SP (US22: 3 + US23: 6) |
| 完了 SP | 9 SP (100%) |
| 完了タスク | 31/31 (0.x 申し送り 15 + US22 5 + US23 11) |
| 主要 commit | 26 件 (096c3be6 〜 1d9dff0c) |
| ADR | 5 件起票・承認 (0016 / 0017 / 0018 / 0019 / 0020) |
| Flyway 新規 | 4 件 (V23 / V26 / V27 / V28) |
| Unit テスト追加 | +60 件以上 (Billing 29 / Tracking 31 / Handling 6 など) |

## Keep (続けるべきこと)

| # | 内容 | 根拠 |
| :--- | :--- | :--- |
| K1 | **Day 1 で ADR 必須決定** | ADR 0019 (Payment 集約境界) を最優先で確定したことで、US23 全タスクの主語が固まり手戻りゼロを達成 |
| K2 | **ADR 駆動の設計判断**: 案 A / 案 B を観点別マトリクスで比較 | ADR 0016 / 0019 で 5 観点比較を実施、後続実装の根拠が明確 |
| K3 | **withOptimisticLock ヘルパの早期抽出 (0.1)** | TrackingCommandService 3 箇所 + その後 BillingCommandService の 4 メソッドで再利用、合計 7 箇所で重複削減 |
| K4 | **公開 Port パターン (BookingPublicApi)** | Handling / Billing が Booking 内部実装に依存しない構造を確立、IT8 中盤の `markSettled` 追加もメソッド 1 行追記のみで完結 |
| K5 | **段階的 Migration (V23/V26/V27/V28)** | 計画番号通りでなく Out-Of-Order 回避で V26/V27/V28 採番、Flyway デフォルト動作と整合 |
| K6 | **同値クラステストの計画的拡充 (0.6)** | TrackingException 4 例外型 + デフォルト escalationFlag 対称テスト + 再解決上書き仕様明示で +6 件、仕様の自己文書化に寄与 |
| K7 | **設計ドキュメント反映を 0.12 で集中処理** | data-model / domain-model / ui_design の 3 文書を一気通貫で ADR 0019 整合に修正、後続実装と乖離ゼロ |

## Problem (改善すべきこと)

| # | 内容 | 影響 |
| :--- | :--- | :--- |
| P1 | **Flyway 番号が計画と実装で乖離** (V24 → V27 / V25 → V28 など) | 計画ドキュメントの番号を更新したが、将来的に migration 番号管理ルール (新規 migration は max+1 で採番) を CLAUDE.md に明記すべき |
| P2 | **Playwright E2E が IT9 申し送り** | US22 / US23 共に E2E スコープを縮小、Shipper 法人マスタ登録 UI 未整備が前提条件不足の根本原因 |
| P3 | **ScalikeJdbcInvoiceRepositoryIT 拡張も IT9 申し送り** | Testcontainers PostgreSQL の起動環境セットアップが IT8 時間枠で吸収できなかった |
| P4 | **detectOverdue が `findAll().count` で全 Invoice 走査** | パフォーマンス問題は当面ないが、Invoice 数 10000 超で N+1 リスク。`findOverdueCandidates(now)` クエリメソッド追加の余地 |
| P5 | **MailNotificationPort の実装が LoggingAdapter のみ** | 実メール送信は IT9 で Pekko Mail / SES 連携必須、本番運用前に PoC 推奨 |
| P6 | **HandlingOrchestrator 単一 TX 化が未実装 (ADR 0016 採択のみ)** | ステップ 2 失敗時のデータ不整合リスクは残存。IT9 で各 Repository を implicit DBSession 受取に拡張する作業が必要 |
| P7 | **コミット粒度のばらつき** | 0.1 (~50 行) と 2.1 (~115 行) でコミットサイズが 2 倍以上、Red→Green 分離も部分的 |
| P8 | **corporate_discount_policy テーブル新設をスキップ** | US22 は Shipper.discountRate 直接参照で機能完結しているが、複数ポリシー併存 (Volume 割引 / 季節割引等) は IT9 で要件確認後に判断 |
| P9 | **BookingCargoSnapshot.corporateDiscountRate に BigDecimal 直接埋め込み** | DiscountRate 値オブジェクトのまま渡せず、Adapter で Double → BigDecimal 文字列経由変換が必要だった (Money.multiplyByRate と同様のパターン化余地) |

## Try (次に試すこと)

| # | 内容 | 担当 / 優先度 |
| :--- | :--- | :--- |
| T1 | **Flyway 番号採番ルールを CLAUDE.md に追記**: 「新規 migration は `ls conf/db/migration` で最大番号 + 1」 | IT9 0.x、優先度: 高 |
| T2 | **Shipper 法人マスタ登録 UI を IT9 で先行整備** | IT9 0.x、優先度: 高 (E2E の前提) |
| T3 | **Playwright E2E 4 件追加** (US22 法人割引適用 / US23 支払発行 / 入金確認 / 期限超過) | IT9、優先度: 高 |
| T4 | **ScalikeJdbcInvoiceRepositoryIT 拡張** (新フィールド永続化 + due_date での検索) | IT9、優先度: 中 |
| T5 | **HandlingOrchestrator 単一 TX 化 (ADR 0016 実装)** | IT9、優先度: 中 (本番デプロイ前) |
| T6 | **MailNotificationPort の Pekko Mail or SES 連携** | IT9、優先度: 中 |
| T7 | **detectOverdue を専用クエリに最適化**: `InvoiceRepository.findOverdueCandidates(now)` 追加 | IT9 / Phase 5、優先度: 低 |
| T8 | **Cron 連携 (Pekko Scheduler)** で detectOverdue を日次起動 | IT9、優先度: 中 |
| T9 | **ArchUnit ルール拡張**: 「`booking.application.commandservices.*` を booking パッケージ外から参照不可」(ADR 0017 コンプライアンス) | IT9、優先度: 低 |
| T10 | **Red→Green コミット分離をより厳密に**: 新メソッド追加時はテスト追加 commit を必ず分離 (CLAUDE.md TDD 規律遵守) | 即時 |

## DoD (Definition of Done) 達成状況

| 項目 | 状態 | 備考 |
| :--- | :---: | :--- |
| US22 受入条件全達成 | ✅ | 4 件 Unit カバー、UI 4 行表示 (適用前/率/額/適用後) |
| US23 受入条件 1 (支払発行) | ✅ | issuePayment 完了、PaymentRequested 通知 + メール送信 |
| US23 受入条件 2 (入金確認) | ✅ | confirmPayment 完了、Cargo.Settled 遷移 + PaymentConfirmed 通知 |
| US23 受入条件 3 (決済機関連携) | 🔄 | **手動 referenceCode 入力に縮小** (S2-3 で IT9 申し送り) |
| US23 受入条件 4 (期限超過) | ✅ | detectOverdue + OverdueAlerted 通知 (Cron 連携 IT9 申し送り) |
| 0.x 申し送り 15 件全消化 | ✅ | H1-H12 + T3 + T6 + ADR 0019/0020 |
| Phase 4 完了 | ✅ | IT7-IT8 全 SP 消化 |
| Release 2.0 GA 到達 | 🔄 | コードレベルは到達、ステージング/本番デプロイは IT9 |
| 設計ドキュメント整合性 | ✅ | 0.12 で data-model / domain-model / ui_design 反映済 |

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
| **IT8** | **9** | **9** | **100%** |
| **累計** | **91** | **91** | **100%** |

平均ベロシティ: 91 SP / 8 イテレーション = **11.4 SP/IT**。IT8 (9 SP) は申し送り 15 件 + 新規 9 SP の総合作業量だったため実質的には平均超過。

## 次イテレーション (IT9) への申し送り

IT9 は **Release 2.0 GA → 2.1 拡張 + 運用基盤強化**を主眼とする想定:

1. **本番運用基盤** (T1-T6 / P5-P6): MailNotificationPort 実装 + Cron + 単一 TX 化
2. **E2E 自動化** (T2-T3): Shipper UI 整備 + Playwright 4 件
3. **外部連携** (T8 + US23-r1): 決済機関連携 / 受取人通知メール
4. **ArchUnit / 品質強化** (T9-T10): ルール拡張 + TDD 規律遵守

## 関連ドキュメント

- [IT8 計画](./iteration_plan-8.md)
- [IT8 完了報告書](./iteration_report-8.md) (本日作成予定)
- [ADR 0016](../adr/0016-handling-orchestrator-transaction-boundary.md) / [0017](../adr/0017-booking-public-api-port.md) / [0018](../adr/0018-mail-notification-port.md) / [0019](../adr/0019-payment-aggregation-vs-invoice-status.md) / [0020](../adr/0020-public-tracking-exception-display.md)
- [IT7 ふりかえり](./retrospective-7.md)
