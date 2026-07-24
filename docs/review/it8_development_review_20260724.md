---
title: IT8 開発成果物レビュー（US23 精算処理・Billing Context 完成）
description: 精算処理の 5 視点マルチパースペクティブレビュー統合レポート
published: true
date: 2026-07-24T00:00:00.000Z
---

# IT8 開発成果物レビュー

対象差分: `bd767f8a..HEAD`（US23 精算処理・Billing Context 完成）。
5 つの XP 視点（programmer / tester / architect / technical-writer / user-representative）で並列レビューし統合した。

## 総合判定

**4 視点がクローズ可（条件付き）・tester がクローズ不可（条件付き差し戻し）**。BC 独立は全 IT 中もっとも厳格（architect）、金額計算・消費税・冪等性は高品質（programmer）、ラベル一元化・ナビ整合は良好（technical-writer）、精算主動線は実用（user-representative）。ただし **受入基準5（期限超過→未払い通知）の駆動経路欠落** が 3 視点で重複指摘され、クローズ前対応を要する。

## 高優先度指摘とクローズ前対応

| # | 視点 | 指摘 | 重大度 | 対応 |
|---|------|------|--------|------|
| 1 | tester/architect/user-rep | `CheckOverdueService` がどのハンドラにも未配線。受入基準5（期限超過→未払い通知）が HTTP/E2E 未実証・実運用で駆動しない（IT7 Try#1 違反・「登録できるが確認できない」の最深部再発） | 高 | **クローズ前修正**: `/billing/invoices/check-overdue` 手動駆動エンドポイント＋一覧に実行ボタン。billing_flow で OVERDUE 遷移＋PAYMENT_OVERDUE（経理宛）を 1:1 実証 |
| 2 | tester | 通知宛先アサート不足（INVOICE_ISSUED＝荷受人・PAYMENT_OVERDUE＝経理の永続化宛先未検証） | 中 | **クローズ前修正**: HTTP テストで宛先・種別をアサート |
| 3 | user-rep | 入金確認後の荷主への「精算完了」通知がない | 中 | **クローズ前修正**: `notify_settlement_completed` を追加し ConfirmPayment で発火 |
| 4 | tech-writer | data-model.md の invoice/payment 列定義がマイグレーションと不一致（charge_total_currency 欠落・discount 列は実装になし・INTEGER→NUMERIC・updated_at） | 中 | **クローズ前修正**: data-model を実装（正典）に合わせる |
| 5 | tech-writer | `is_pending` が Overdue/Refunded でも入金確認ボタン表示（Refunded は不自然） | 低 | **クローズ前修正**: Pending/Overdue のみ表示 |
| 6 | user-rep | 入金明細（入金日・取引参照番号）が精算書画面に残らない（監査） | 低 | **クローズ前修正**: invoice_show に入金明細を表示 |
| 7 | programmer | ReqwestPaymentGateway に timeout 未設定 | 低 | **クローズ前修正**: connect/read timeout 設定 |

## 中・低指摘（Release 1.2 バックログへ・方針明記）

| 視点 | 指摘 | 方針 |
|------|------|------|
| user-rep/architect | 実決済 ACL（ReqwestPaymentGateway）未結線・StubPaymentGateway が既定 | Release 1.2: 本番/開発プロファイルで PaymentGateway を切替（AppState 注入化・programmer 提案）。契約テストは済み |
| programmer | `SqlxInvoiceRepository::reconstruct` が InvoiceId 再生成（集約同一性） | Release 1.2: invoice_id カラム往復 or InvoiceId を集約から外し invoice_number に一本化（ADR） |
| programmer/architect | CheckOverdue の find_all 全走査・通知失敗の部分適用 | Release 1.2: `find_overdue_candidates(as_of)` ポート追加・通知失敗の継続/冪等化 |
| architect | per-handler の service/ACL 組立（Try#6 未返済） | Release 1.2: composition root へ引き上げ |
| user-rep | 一覧の未払い/超過の強調・状態フィルタなし | Release 1.2: UX 改善 |
| tester | 消費税丸めの切り捨て側境界テスト | Release 1.2: 対称ケース追加 |
| architect | ConfirmPayment 順序を ADR-0004 に Billing 適用例として追記 | 随時 |

## 良い点（維持すべき規律）

- BC 独立が全 IT 中もっとも厳格（domain-billing は shared-kernel のみ・settle 連動も ACL 経由・architect）
- 金額計算の規律（Decimal・名前付き定数・純粋関数 calculate_tax・四捨五入集約・programmer）
- 決済ゲートウェイ wiremock 契約 3 種（CONFIRMED/402/PENDING・tester 模範評価）
- 受入基準4（settle 連動）を HTTP で SETTLED まで実証（Try#1 模範・tester）
- PaymentStatus ラベル一元化・消費税 % 整形（IT7 の生 Decimal 表示問題の再発なし・tech-writer）

## 関連ドキュメント

- [IT8 計画](../development/iteration_plan-8.md)
- [ADR-0009 輸送料金と精算書の段階分割](../adr/0009-freight-charge-and-invoice-separation.md)
- [ADR-0010 Money の BC ローカル定義](../adr/0010-billing-money-value-object.md)
