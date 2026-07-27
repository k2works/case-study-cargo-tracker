---
title: イテレーション 8 完了報告書
description: IT8（US21 料金算出・US22 法人割引・US23 精算・US01 ダッシュボード・US-ADM-01 割引ポリシー管理）完了報告。Billing Context 新設で Release 1.0（全 25 US）到達。
tags: development, iteration-report, iteration-8, go, release-1.0
---

# イテレーション 8 完了報告書

## エグゼクティブサマリー

最終イテレーション IT8 で **Billing Context（精算）を新設**し、US21（輸送料金算出）・US22（法人割引）・US23（精算）を実装。引取済み（CLAIMED）貨物の輸送実績から基本料金を算出し、法人荷主には Shipper ACL 経由の契約割引率を適用、請求書を発行・通知し、入金確認で予約を精算済み（SETTLED）にするフローを完成させた。これにより **Phase 3 が完了し、全 25 ユーザーストーリーを実装した Release 1.0（全機能）に到達**した。

加えて、ウォーキングスケルトンでプレースホルダ表示だった 2 画面をクローズ時に実装した: **US01 ダッシュボード**（全ロール向けサマリー・導線）と **US-ADM-01 割引ポリシー管理**（独立 BC「Discount Policy Context」を新設・CRUD）。BC 正典は 7→8 に更新（ADR-0010）。

## 達成状況

| ユーザーストーリー | SP | 状態 |
| :--- | :--- | :--- |
| US21 輸送料金算出 | 5 | 完了 |
| US22 法人割引適用 | 3 | 完了 |
| US23 精算処理 | 5 | 完了 |
| US01 ダッシュボード（クローズ時追加） | - | 完了 |
| US-ADM-01 割引ポリシー管理（クローズ時追加） | - | 完了 |

- 計画 13 SP（精算）を達成。プレースホルダ 2 画面を追加実装。
- **Release 1.0 完了条件（全 25 US 実装）を達成**。

### 成功基準

- [x] US21/US22/US23 受入基準を充足（料金算出・確定・法人割引・発行・通知・入金確認・SETTLED・OVERDUE）。
- [x] Billing ドメイン層カバレッジ 94.4%（目標 90%+）。
- [x] 法人割引率を Shipper ACL で参照・`make arch` green（BC 直接依存なし）。
- [x] SonarQube Quality Gate PASS（new_coverage 81.1%・violations 0・重複 0.49%・Bug 0・Vulnerability 0）。
- [x] `make check` green・integration（testcontainers）green。
- [x] US23 フルフロー E2E（発行→法人割引→入金確認→精算済み）を追加（seed 000019）。

## 技術的成果

### 実装

- **Billing Context 新設**: `Invoice` 集約（発行・入金確認・OVERDUE）・`Money`（int64・Add/Subtract/MultiplyRate）・`DiscountRate`・`DiscountPolicy`（VO）・`PaymentStatus`。ACL ポート（`CargoBillingSnapshotProvider`・`ShipperContractProvider`・`BookingSettler`・`InvoiceNumberIssuer`・`NotificationPort`）。pgx リポジトリ・invoice/payment テーブル（000017）。請求書一覧・詳細・入金確認画面（ROLE_BILLING）。
- **請求番号原子採番（ADR-0008 返済）**: 共有 `sequence_counter`（000016）で `INV-YYYYMMDD-NNNN` を単一文採番。追跡番号採番も同パターンに統一（Day 1 返済）。
- **ダッシュボード（US01）**: `SummaryProvider`（DIP）で各 BC クエリを合成ルートで横断集約。ロール別サマリーカードと主要画面導線。
- **Discount Policy Context 新設（US-ADM-01・ADR-0010）**: 割引ポリシー集約（種別・割引率・有効期間）・CRUD・`discount_policy` テーブル（000018）・直接 pgx リポジトリ。

### コード規模

- Billing / Discount Policy: Go ファイル 30・テスト関数 70（domain/application/interfaces のユニット＋ integration）。

## 品質指標

| 指標 | 値 |
| :--- | :--- |
| billing ドメイン層カバレッジ | 94.4% |
| discountpolicy ドメイン層カバレッジ | 92.7% |
| SonarQube Quality Gate | PASS |
| new_coverage | 81.1%（閾値 80%） |
| new_violations | 0 |
| 重複率（new） | 0.49%（閾値 3%） |
| Bug / Vulnerability | 0 / 0 |
| `make check` / `make arch` | green / green |
| integration（testcontainers） | green |

- CI: ブランチが手動トリガー（workflow_dispatch）で IT8 コードに対しては未実行。ローカル同等ゲート＋ SonarQube PASS で品質担保。push/CI 実行は保留。

## レビュー結果

5 視点（programmer/tester/architect/technical-writer/user-representative）並列レビューを実施（[IT8 開発レビュー](../review/it8_go_review_20260727.md)）。**高優先度 3 件をクローズ前に全対応**:

1. 支払期限 DATE/TIMESTAMP 境界バグ（`MarkOverdue`・`IsActiveOn`）→ 日付単位比較へ正規化・当日時刻付き境界テスト追加。
2. US23 フルフロー E2E 欠落 → billing.spec.ts に追加・CLAIMED seed（000019）。
3. 新 BC が正典 ADR-0002・domain-model.md 未反映 → ADR-0010 起票・正典 8 化・設計ドキュメント更新。

中の一部（割引ポリシーのフォームエラー写像・境界テスト・用語統一・請求書 ID 形式）も対応。

## 課題と残作業（Release 1.0 後バックログ）

- **金額の int32 サイレントクランプ**（中）: `invoice.*_value` を BIGINT へ移行し発行時オーバーフローをエラー化。
- **入金確認のクロス BC 部分失敗リカバリ**（中）: `ConfirmPayment` を冪等化 or アウトボックス化。
- **ADR-0009 系**: エスカレーション再評価・`TrackingExceptionDetectedEvent` 配信・管理職ワークリスト。
- **T3b**: 荷役履歴リプレイ。**US21**: 例外時料金調整。
- **UX（低）**: 請求一覧の状態フィルタ・期限ソート・未入金/期限超過の切り分け表示、ダッシュボード ui_design 追随・割引ポリシー salt 図追補。
- **CI**: リモート CI での IT8 コード検証（push 保留）。

## 次サイクル（保守・拡張フェーズ）への引き継ぎ

Release 1.0 到達により以降は保守・拡張フェーズ。上記バックログを保守バックログとして一元管理。詳細は [IT8 ふりかえり](./retrospective-8.md) の Try を参照。

## 関連ドキュメント

- [イテレーション 8 計画](./iteration_plan-8.md)
- [IT8 開発レビュー統合レポート](../review/it8_go_review_20260727.md)
- [IT8 ふりかえり（KPT）](./retrospective-8.md)
- [ADR-0010 割引ポリシー管理コンテキスト](../adr/0010-discount-policy-context.md)
