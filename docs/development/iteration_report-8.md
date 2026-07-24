---
title: イテレーション 8 完了報告書 - 精算処理・Billing Context 完成・Release 1.1 完成
description: IT8（US23）精算処理の完了報告。Billing Context 完成と Release 1.1（例外対応・請求）の全機能実装完了
published: true
date: 2026-07-24T00:00:00.000Z
---

# イテレーション 8 完了報告書

## エグゼクティブサマリー

| 項目 | 内容 |
|------|------|
| **イテレーション** | 8（精算処理・Billing Context 完成） |
| **期間** | 2026-07-24（実績・集中実装セッション） |
| **局面** | 終盤（アウトサイドイン・予備イテレーション兼安定化） |
| **計画 SP / 実績 SP** | 5 / 5 |
| **達成率** | 100%（機能スコープ） |
| **対象ストーリー** | US23（精算を処理する） |
| **主要成果** | Billing Context を精算まで完成。確定料金→精算書発行（消費税10%）→荷主通知→決済機関連携で入金確認→精算済＋予約 Settled→精算完了通知、期限超過→未払い通知までを全層で実装。**Phase 3 完了・累計 97/97 SP（100%）・Release 1.1（例外対応・請求）の全機能が実装完了**。5 視点レビュー高優先度 7 件をクローズ前返済 |

IT8 は終盤（アウトサイドイン）の最終イテレーションとして、中盤〜終盤で実装済みの各集約（FreightCharge・Booking）を業務シナリオ起点で束ね、US23 精算処理で Billing Context を完成させた。Invoice 集約・Payment・PaymentStatus・消費税計算をドメインに、精算書発行/入金確認/期限超過チェックの 3 ユースケースを app 層に実装し、決済機関連携（PaymentGatewayPort・reqwest+wiremock 契約テスト）・予約精算連携（BookingSettlementPort・Cargo::settle）・通知（InvoiceNotificationPort）を ACL に隔離した（BC 独立は全 IT 中もっとも厳格・architect 評価）。非機能受け入れ（cargo audit/deny 緑）も通過。5 視点レビューで 3 視点が重複指摘した「CheckOverdueService 未配線（受入基準5 が実運用で駆動しない）」をクローズ前に手動駆動導線＋HTTP 実証で返済した。累計 97/97 SP（100%）で Release 1.1 の全機能が計画通り完成した。

## 1. イテレーション概要

### 1.1 目的と背景

経理担当者が確定した輸送料金をもとに精算書を発行し、荷主通知・決済機関連携による入金確認・精算完了（予約状態 Settled）まで（US23）を成立させ、Billing Context を完成させる。これにより Release 1.1（例外対応・請求）の請求業務を締め、GA に向けたリリース準備を整える。

### 1.2 スコープ

| ID | ストーリー | SP | 結果 |
|----|-----------|----|----|
| US23 | 精算を処理する | 5 | 完了（発行・荷主通知・入金確認・精算済/予約Settled・期限超過→未払い通知） |
| **合計** | | **5** | **完了** |

スコープ外（Release 1.2）: 実決済 ACL の本番結線・CheckOverdue のバッチ自動化・通知 SMTP 実配信・per-handler DI 整理（Try#6）・rank 一元化（Try#5）・dashboard 拡充（Try#7）・ui_design salt 追記。

## 2. 達成状況

### 2.1 ストーリー別受入条件（US23・全 5 基準）

- **確定料金から精算書発行**: `Invoice::issue` が確定料金（`FreightCharge.total()`＝割引後）に消費税 10% を適用し請求金額を確定（例: 200,000＋税 20,000＝220,000）。請求番号（`INV-`＋24 桁・VARCHAR(30) 制約内）・支払期限（発行日＋30 日）を採番。未確定料金は 422 で拒否。
- **荷主にメール通知**: `SqlxInvoiceNotificationPort` が INVOICE_ISSUED を荷受人宛に記録（宛先・種別を統合テストでアサート）。
- **決済機関連携で入金確認**: `ConfirmPaymentService` が `PaymentGatewayPort` で入金確認→`Payment` 記録→Invoice Confirmed。`ReqwestPaymentGateway` を wiremock で CONFIRMED/402/PENDING の 3 契約検証。
- **入金後 精算済＋予約 Settled**: `Cargo::settle()`（Delivered→Settled）を `BookingSettlementPort` ACL 経由で駆動。HTTP テストで `booking_status='SETTLED'` まで実証。入金確認後に SETTLEMENT_COMPLETED を荷主へ通知。
- **期限超過→経理へ未払い通知**: `CheckOverdueService`＋`Invoice::mark_overdue`。**手動駆動エンドポイント `/billing/invoices/check-overdue`＋一覧ボタン**を提供し、HTTP テストで OVERDUE 遷移＋PAYMENT_OVERDUE（経理・ROLE_BILLING 宛）を 1:1 実証。

### 2.2 局面移行の一貫性

終盤（アウトサイドイン）の最終として、US23 を受入（HTTP/E2E）起点で設計し不足ドメイン（Invoice・消費税・settle）を補完。IT3-7 の ACL パターンを厳守し、決済・予約連携・通知を ACL に隔離した。

## 3. 技術的成果

### 3.1 実装（レイヤー別）

| レイヤー | 成果物 |
|---------|--------|
| domain-billing | `Invoice` 集約（issue/confirm_payment/mark_overdue・消費税 calculate_tax 純粋関数）・`InvoiceLineItem`・`Payment`・`PaymentStatus`・`PaymentMethod`・`InvoiceId`・`InvoiceRepository` ポート（19 tests） |
| domain-booking | `Cargo::settle()`（Delivered→Settled・網羅的 match・29 tests） |
| app-billing | `GenerateInvoiceService`・`ConfirmPaymentService`（通知結線）・`CheckOverdueService`・`PaymentGatewayPort`/`BookingSettlementPort`/`InvoiceNotificationPort` ACL（12 tests） |
| infra-persistence | `20261014000001_it8_invoice_payment.sql`（invoice/invoice_line_item/payment・NUMERIC(15,2) 統一）・`SqlxInvoiceRepository`（統合 2 tests） |
| infra-external | `ReqwestPaymentGateway`（timeout 設定・wiremock 契約 3 tests） |
| interface-web | 精算書ハンドラ（一覧/発行/詳細/入金確認/期限超過チェック）・`billing_acl`（CargoBookingSettlement・SqlxInvoiceNotificationPort・StubPaymentGateway）・精算書画面・通知履歴/推定到着日（billing_flow 9 tests） |

### 3.2 テスト結果

| 種別 | 件数・結果 |
|------|-----------|
| 単体 | domain-billing 19・app-billing 12・domain-booking 29 ほか全 green |
| 統合（testcontainers） | billing_flow 9・invoice_repository 2・既存フロー全 green（回帰なし） |
| 契約（wiremock） | payment_gateway 3（CONFIRMED/402/PENDING） |
| E2E（Playwright） | it8-demo 2（精算書発行→入金確認→精算完了・一覧確認） |
| 非機能 | cargo audit・cargo deny check advisories 緑（推移的アドバイザリ 3 件を本番非露出の根拠付きで ignore） |
| Lint/フォーマット | clippy `-D warnings` クリーン・fmt 準拠（`+stable` 1.97.1） |

## 4. 品質指標

| 指標 | 実績 |
|------|------|
| 達成率 | 100%（US23 全 5 受入基準・機能スコープ） |
| ベロシティ | 5 SP（8 IT 連続で計画ラインと完全一致・安定） |
| 累計進捗 | **97/97 SP（100%）・Release 1.1 全機能実装完了** |
| BC 独立性 | 違反なし（全 IT 中もっとも厳格・architect 検証） |
| レビュー | 5 視点・高優先度 7 件クローズ前返済 |

## 5. レビュー結果

5 視点マルチパースペクティブレビュー（[レポート](../review/it8_development_review_20260724.md)）で 4 視点がクローズ可・tester がクローズ不可（条件付き差し戻し）。高優先度 7 件をクローズ前に返済:

1. **CheckOverdueService 未配線 → 手動駆動エンドポイント＋一覧ボタン＋HTTP 実証**（tester/architect/user-rep 重複指摘・受入基準5）
2. INVOICE_ISSUED 宛先アサート追加（tester）
3. 精算完了通知（SETTLEMENT_COMPLETED・荷主宛）追加（user-rep）
4. data-model 列定義を実装に整合（tech-writer）
5. 入金確認ボタンを Pending/Overdue のみ表示（tech-writer）
6. 精算書詳細に入金明細表示（user-rep・監査）
7. ReqwestPaymentGateway に timeout 設定（programmer）

## 6. 課題と残作業（Release 1.2 バックログ・正直な記録）

- **実決済 ACL の本番結線**: 既定は StubPaymentGateway（送信＝記録）。ReqwestPaymentGateway は契約テスト済みだが未結線。切替を composition root（環境変数/プロファイル）で行う設計を Release 1.2 で標準化。
- **CheckOverdue のバッチ自動化**: 現状は手動駆動導線。定期スケジューラ＋`find_overdue_candidates` 絞り込みポート・通知失敗の冪等化を Release 1.2。
- **reconstruct の InvoiceId 恒常化・per-handler DI 整理（Try#6）・rank 一元化（Try#5）・dashboard/一覧フィルタ（Try#7）・通知 SMTP 実配信・ui_design salt 追記** を Release 1.2。

## 7. 次イテレーション／リリースへの引き継ぎ

- **Release 1.1 の全機能が実装完了**（累計 97/97 SP・100%）。`creating-release-report` で **Release 1.1（例外対応・請求）完了報告書**を作成し GA 判断へ。
- **GA 前の推奨対処**（user-rep）: 実決済 ACL 結線・CheckOverdue バッチ自動化。手動導線は返済済み。
- Release 1.2 バックログ（上記 6 章）を計画に積む。

## 関連ドキュメント

- [イテレーション 8 計画](./iteration_plan-8.md)
- [イテレーション 8 ふりかえり](./retrospective-8.md)
- [IT8 開発成果物レビュー](../review/it8_development_review_20260724.md)
- [ADR-0009 輸送料金と精算書の段階分割](../adr/0009-freight-charge-and-invoice-separation.md)
- [ADR-0010 Money の BC ローカル定義](../adr/0010-billing-money-value-object.md)
- [リリース計画](./release_plan.md)
