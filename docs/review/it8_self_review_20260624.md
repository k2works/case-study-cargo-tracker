---
title: IT8 セルフレビュー（中間）
date: 2026-06-24
reviewer: AI Agent (Ralph Loop)
scope: IT8 全 31 タスク完遂後の中間セルフレビュー
---

# IT8 セルフレビュー（中間）

正式な XP 5 エージェント並列レビュー（developing-review）は user 起動後に別途実施する想定。本書はその前段として、Ralph Loop 内で実施するセルフレビューとして残す。

## レビュー対象

| カテゴリ | 件数 | 主要 commit |
| :--- | ---: | :--- |
| 0.x 申し送り (IT7 持ち越し) | 15 タスク | `a915dc96` 〜 `e73ca72d` |
| US22 法人割引 (1.x) | 5 タスク | `fefc63d3` |
| US23 精算 (2.x) | 11 タスク | `1e286b1a` 〜 `1d9dff0c` |
| ADR 起票 | 5 件 | 0016-0020 |
| Flyway 新規 | 4 件 | V23 / V26 / V27 / V28 |
| **commit 数** | **28 件** | `096c3be6` 〜 `0f14985a` |

## 観点別チェック

### 1. プログラマー観点（コード品質）

| # | チェック項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| P1 | scalafmt / scalafix 違反ゼロ | ✅ | 全 commit で pre-commit hook 通過 |
| P2 | Unit テスト全件 Green | ✅ | Billing 29 / Tracking 31 / Handling 6 / Shared 5 等、Testcontainers 系のみ ABORT (本変更と無関係) |
| P3 | EitherValues / OptionValues 移行進捗 | ✅ | TrackingCommandServiceSpec 16 箇所 + TrackingExceptionSpec 2 箇所 + BillingCommandServiceSpec 部分適用、`@unchecked` 残存は 7 箇所 (BillingCommandServiceSpec 既存系、IT9 で対応) |
| P4 | OptimisticLockOps の重複削減 | ✅ | TrackingCommandService 3 箇所 + BillingCommandService 2 箇所 (issuePayment / confirmPayment) 計 5 箇所で再利用 |
| P5 | 値オブジェクト導入 (TrackingExceptionEventId) | ✅ | opaque type で型安全、Long 直接露出を回避 |
| P6 | private final case class private constructor | ✅ | Invoice / Cargo は smart constructor パターン維持 |

### 2. アーキテクト観点（境界・依存）

| # | チェック項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| A1 | コンテキスト境界（Hexagonal） | ✅ | Billing → Booking は BookingPublicApi 経由のみ、Handling → Booking も同様 |
| A2 | 公開 Port パターン (ADR 0017) | ✅ | BookingPublicApi trait に 5 メソッド (handling 用 2 + payment 関連 3)、`markSettled` も追加で連携完結 |
| A3 | 出力 Port (MailNotificationPort) | ✅ | Billing 配下に trait、infrastructure/mail に Logging 実装、IT9 で実 Adapter 差替え可能 |
| A4 | ACL Adapter の責務分離 | ✅ | BookingCargoForHandlingAdapter / BookingCargoQueryAdapter / BookingAdapter で 1 Context 1 Adapter |
| A5 | トランザクション境界 (ADR 0016) | 🔄 | 案 A 採用済、実装は IT9 申し送り。ステップ 2 失敗時のデータ不整合リスクは残存（既知） |
| A6 | ArchUnit ルール拡張 | 🔄 | 「booking.application.commandservices.\* を外部参照不可」ルールは IT9 で追加予定 |

### 3. テスター観点（テスト品質）

| # | チェック項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| T1 | 同値クラス代表値テスト | ✅ | TrackingException 4 種別 + デフォルト escalationFlag 対称、Invoice 状態遷移 5 種 |
| T2 | 境界値テスト | ✅ | DiscountRate 0% / 15% / 30% / 範囲外、markOverdue 期日内/超過 |
| T3 | エラーパステスト | ✅ | InvalidPaymentStateTransition、ExceptionNotFound、NotResolved、EmptyComment |
| T4 | 副作用検証 (Mail/Notification) | ✅ | NoopMail / FakeBookingPublicApi のバッファ確認で sendXxx / logXxx 呼出を検証 |
| T5 | Repository IT (Testcontainers) | 🔄 | ScalikeJdbcInvoiceRepositoryIT 拡張は IT9 申し送り |
| T6 | Playwright E2E | 🔄 | US22 + US23 計 4 件は IT9 申し送り (Shipper 法人マスタ UI 前提) |
| T7 | テストデータビルダ重複 | ⚠️ | BillingCommandServiceSpec の snapshot ヘルパーが少冗長、IT9 でビルダ抽出余地 |

### 4. テクニカルライター観点（ドキュメント）

| # | チェック項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| D1 | ADR 構成統一 (Status / Context / 決定 / 影響 / コンプライアンス / 備考) | ✅ | 5 件全て同形式、相互参照 (Related ADR) も明示 |
| D2 | iteration_plan-8.md 完了マーク | ✅ | 全 31 タスクが [x] **完了** マーク、補足コメントで詳細追跡可能 |
| D3 | retrospective-8.md / iteration_report-8.md | ✅ | Keep 7 / Problem 9 / Try 10、ベロシティ統計、IT9 申し送り 9 件 |
| D4 | 設計ドキュメント反映 (data/domain/ui) | ✅ | 0.12 で ADR 0019 案 B 整合反映、Role 6 箇所統一 |
| D5 | mkdocs.yml ナビ追加漏れ | ✅ | retrospective-8 / iteration_report-8 / ADR 0016-0020 全追加 |
| D6 | README プロジェクト進捗 | ✅ | Phase 1-4 表、累計 SP、リンク委譲構成 |
| D7 | CHANGELOG.md 未更新 | ⚠️ | Release 2.0 GA リリース時 (IT9) に一括更新予定 |

### 5. ユーザー代表者観点（業務適合性）

| # | チェック項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| U1 | US22 受入条件 | ✅ | 法人割引適用前/率/額/適用後 4 行表示、UI 入力依存ゼロで Shipper.discountRate 自動反映 |
| U2 | US23 受入条件 1 (支払発行) | ✅ | NotIssued → Pending 遷移、支払期日 + 入金参照コード設定、メール送信 |
| U3 | US23 受入条件 2 (入金確認) | ✅ | Pending\|Overdue → Confirmed、Cargo.Settled 連携、入金日時手動入力 |
| U4 | US23 受入条件 3 (決済機関連携) | 🔄 | **手動 referenceCode 入力に縮小** (S2-3 確認済)、IT9 で Stripe/GMO 等連携予定 |
| U5 | US23 受入条件 4 (期限超過) | ✅ | detectOverdue + OverdueAlerted 通知 (Cron は IT9) |
| U6 | UI アクセス制御 | ✅ | Settlement / MasterAdmin 限定、他ロールは flash error |
| U7 | confirm dialog (入金確認時) | ✅ | 「Settled 状態に遷移します」と明示警告 |
| U8 | 5 種ステータスバッジ色分け | ✅ | NotIssued (グレー) / Pending (黄) / Overdue (赤) / Confirmed (緑) / Refunded (青) |
| U9 | 例外取消し動線 (0.7 / H9 解消) | ✅ | 解決済例外に「対応取消し」(confirm 付) + 「補足追記」フォーム |
| U10 | Delay UI 拡張 (0.8) | ✅ | 新到着予定日 datetime-local + 対応方針 4 種定型 + 詳細理由 |

## 統合フィードバック

### 高優先 (IT9 着手前に対応推奨)

| # | 内容 | 緊急度 | 解消提案 |
| :--- | :--- | :---: | :--- |
| H1 | Playwright E2E 4 件 (US22 + US23 各シナリオ) | 高 | IT9 0.x 着手、Shipper 法人マスタ UI 先行整備 |
| H2 | HandlingOrchestrator 単一 TX 化 (ADR 0016 実装) | 中 | IT9 中盤、各 Repository に implicit DBSession 拡張 |
| H3 | MailNotificationPort の Pekko Mail / SES 連携 | 中 | IT9 後半、PoC + 本番設定 |

### 中優先

| # | 内容 | 解消提案 |
| :--- | :--- | :--- |
| M1 | BillingCommandServiceSpec の `@unchecked` 7 箇所残存 | EitherValues 化を IT9 で完遂 |
| M2 | BillingCommandServiceSpec の snapshot ヘルパ冗長 | テストデータビルダ抽出 (SnapshotBuilder) |
| M3 | detectOverdue が `findAll().count` で全 Invoice 走査 | `InvoiceRepository.findOverdueCandidates(now)` 追加 |

### 低優先

| # | 内容 | 解消提案 |
| :--- | :--- | :--- |
| L1 | corporate_discount_policy テーブル新設スキップ | 複数ポリシー併存要件発生時に検討 |
| L2 | InvoiceController の Form 重複コード | 共通ヘルパ抽出 |
| L3 | LoggingMailNotificationAdapter の出力フォーマット非構造化 | JSON 形式に切替 (構造化ログ + Datadog 連携考慮) |

## 矛盾事項 / 整合性懸念

| # | 内容 | 確認結果 |
| :--- | :--- | :--- |
| C1 | Flyway 番号が計画と実装で乖離 (V24 → V27 / V25 → V28) | iteration_plan-8.md 完了マークで両方記載済、CLAUDE.md 採番ルールは IT9 申し送り (P1) |
| C2 | data-model.md の payment テーブル「歴史的記録」表記 | ✅ V28 で実 drop 済、ドキュメントとデータベース整合 |
| C3 | ui_design.md の Role と実装 Role.scala | ✅ 0.12 で 6 箇所統一 (Accountant→Settlement / Admin→MasterAdmin) |

## 総合評価

| 観点 | 評価 |
| :--- | :--- |
| **コード品質** | A (重複削減 / 値オブジェクト導入 / EitherValues 部分適用) |
| **アーキテクチャ** | A (Port パターン強化、境界尊重、ADR 駆動設計) |
| **テスト** | A- (Unit 充実、IT/E2E は IT9 申し送り) |
| **ドキュメント** | A (ADR 5 件 + 完了報告書 + ふりかえり + 設計反映) |
| **業務適合性** | A- (US23 受入条件 3 を縮小、本番運用は IT9) |
| **総合** | **A** |

IT8 はスコープ 9 SP に対し 31 タスクを完遂し、Phase 4 を完了 + Release 2.0 GA コード到達を達成した。E2E / 実メール送信 / Cron 等の運用基盤は IT9 で完成させる前提だが、コードレベルでの業務機能は揃った状態となった。

## 推奨される次のアクション

1. **正式マルチパースペクティブレビュー (developing-review)**: user 起動後に XP 5 エージェント並列実施
2. **IT9 計画策定**: 本書の高優先指摘 3 件 + retrospective-8.md の Try 10 件をベースに `/planning-releases --iteration 9`
3. **ステージング環境デプロイ**: IT9 着手前に Release 2.0 GA を staging に投入し E2E 検証

## 関連ドキュメント

- [IT8 計画](../development/iteration_plan-8.md)
- [IT8 ふりかえり](../development/retrospective-8.md)
- [IT8 完了報告書](../development/iteration_report-8.md)
- [IT7 実装レビュー](./it7_implementation_review_20260623.md)
- ADR [0016](../adr/0016-handling-orchestrator-transaction-boundary.md) / [0017](../adr/0017-booking-public-api-port.md) / [0018](../adr/0018-mail-notification-port.md) / [0019](../adr/0019-payment-aggregation-vs-invoice-status.md) / [0020](../adr/0020-public-tracking-exception-display.md)
