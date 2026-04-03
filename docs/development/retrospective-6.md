---
title: イテレーション 6 ふりかえり
description: IT6 の Keep / Problem / Try を整理し、v1.0.0 リリース後の改善方針をまとめたふりかえり記録。
published: true
date: 2026-04-03T00:00:00.000Z
tags: retrospective, it6
---

# イテレーション 6 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT6 |
| 計画期間 | 2026-06-09 〜 2026-06-22 |
| 実績期間 | 2026-04-03 |
| 対象ストーリー | US17 / US18 |
| 計画 SP | 8 |
| 実績 SP | 8 |
| テスト件数 | 506 件（全 Green） |
| カバレッジ | 89.2%（instruction） |
| SonarQube | Quality Gate PASS（new_violations: 0、new_coverage: 90%） |
| レビュー指摘対応 | 高優先度 H1〜H6 全件対応完了 ✅ |

## Keep

### 技術面

- **billing BC の ACL パターンを一貫して適用できた**: US17（法人割引）では Billing BC が Shipper BC の割引率を取得するために `ShipperDiscountQueryPortAdapter` を実装し、BC 境界を越えた参照をインターフェース（`ShipperDiscountQueryPort`）で抽象化しました。IT5 で確立した `FreightBookingQueryPortAdapter` のパターンを踏襲することで、実装のブレなく ACL を追加できました。

- **Invoice 集約の状態遷移ロジックを TDD で正確に実装できた**: US18 では `Invoice.confirmPayment()` の PENDING → CONFIRMED のみ許可する不変条件、`InvoiceCommandService` での重複 Invoice 防止（H2 修正）と bookingId 整合性検証（H3 修正）をすべてユニットテストで先に記述してから実装しました。レビューで発見された問題をテストでドキュメント化したことで、修正後の正確性を確認できました。

- **5 視点並列コードレビューで高優先度欠陥 6 件を同一イテレーション内に修正**: xp-programmer・xp-tester・xp-architect・xp-technical-writer・xp-user-representative の 5 エージェントによる並列レビューで H1（例外ハンドラー欠落）・H2（重複 Invoice 防止）・H3（bookingId 不整合）・H4・H5（PaymentStatus バッジ色）・H6（ボタン確認ダイアログ）の全 6 件を発見・修正し、品質ゲートを通過しました。

- **SonarQube Quality Gate を連続 6 イテレーション PASS**: IT1 から IT6 まで全イテレーションで new_violations: 0・カバレッジ閾値超過を達成しました。IT6 の新規実装（billing BC の割引・精算機能）では Code Smell を一件も追加せず、既存 10 件はすべて billing 以外の BC に存在する record 化推奨（次バージョンで対応予定）です。

- **E2E テスト 23 件（e2e/ パッケージ累計）全通過**: US17（法人割引：2 件）・US18（精算処理：3 件）の E2E テストを追加し、IT5 で確立した `createConfirmedBooking()` ヘルパーパターンと `cleanUp()` FK 順序（invoices → freight_charges → ...）を正確に踏襲しました。

### プロセス面

- **IT5 の Try 事項「E2E テスト作成前に DTO 仕様を確認する」を徹底**: US17・US18 の E2E テスト作成時に `ApplyDiscountRequest`・`GenerateInvoiceRequest` 等の DTO 必須フィールドを事前に確認してからリクエストボディを構築し、400 エラーによる手戻りを防止しました。

- **コミット前 `./gradlew test` 全件確認を徹底**: staged 変更のコミット前に毎回テスト実行を挟み、E2E を含む全スイートが GREEN であることを確認してからコミットするルールを遵守しました。

- **UI/UX レビューで IT5 積み残し（確認ダイアログ）を解消**: H6 で `billing/list.html` の確定ボタンに `onclick="return confirm(...)"` を追加し、IT5 から backlog に積んでいた確認ダイアログ要件を IT6 内に解消しました。

## Problem

### 設計・実装

- **支払期限 30 日のマジックナンバーが残っている**: `InvoiceCommandService.generateInvoice()` 内の `plusDays(30)` がハードコードされています。`InvoicePaymentPolicy` ドメインサービスに抽出する設計が望ましいですが、今 IT では未対応です。

- **`InvoiceRepositoryImpl.save()` に DRY 違反が残っている**: INSERT と UPDATE で `InvoiceRecord` の構築コードが重複しています。`toRecord()` ヘルパーメソッドへの抽出が必要ですが、今 IT では未対応です。

- **`PaymentStatus.OVERDUE` / `REFUNDED` の遷移ロジックが未実装**: ドメインモデル（`PaymentStatus` enum）には OVERDUE・REFUNDED が定義されていますが、バッチ処理や返金処理の具体的なユースケースは未実装です。v1.1.0 以降のスコープです。

### 品質管理

- **中・低優先度のレビュー指摘が次バージョンに持ち越し**: 精算書の金額に通貨単位（¥）が未表示、支払期限の日付フォーマット（`yyyy-MM-dd` → `yyyy年MM月dd日`）、ナビゲーションの active 状態なし、`role="alert"` 未設定など UI/UX の改善点が残っています。

- **`InvoiceQueryService.paymentStatus` が日本語 String を返している**: REST API のレスポンスが日本語（"支払い待ち" 等）となっており、国際化対応や API クライアントとの互換性の観点で enum 名（PENDING 等）の返却が望ましいです。

- **instruction カバレッジが IT5 比で微減（90.7% → 89.2%）**: 新規追加コード（billing BC）の一部パスがテストで網羅されていないことが原因です。分岐カバレッジの計測・記録が IT6 でも不十分でした。

## Try

| Try | 担当 | 期限 | 期待効果 |
|-----|------|------|----------|
| `InvoicePaymentPolicy` ドメインサービスを実装して支払期限ポリシーを外部化する: 30 日のマジックナンバーを `InvoicePaymentPolicy` クラスに抽出し、ポリシーを設定ファイルで変更できるようにする | Copilot | v1.1.0 | ビジネスルール変更時の影響範囲を局所化できる |
| `InvoiceRepositoryImpl.save()` の DRY 違反を解消する: `toRecord(Invoice)` ヘルパーを抽出して INSERT / UPDATE 両方から呼び出すようにリファクタリングする | Copilot | v1.1.0 | コード重複を排除し、フィールド追加時の修正箇所を 1 か所に集約できる |
| `InvoiceQueryService.paymentStatus` を enum 名（英語）で返すようにする: REST API レスポンスの `paymentStatus` フィールドを `PaymentStatus.name()` で返し、フロントエンドやクライアントが機械可読な値を扱えるようにする | Copilot | v1.1.0 | API の国際化対応と将来的な多言語対応を容易にする |
| UI の accessibility 改善（`role="alert"`・active nav・通貨記号・日付フォーマット）を v1.1.0 backlog に積む: 残存する UI/UX 指摘事項を GitHub Issue として登録し、次リリース計画のスコープ選定に含める | Copilot | v1.1.0 計画時 | 技術的負債をトラッキングし、利用者体験の継続的改善を確保できる |
| `PaymentStatus.OVERDUE` / `REFUNDED` の遷移ロジックを v1.1.0 で実装する: 支払期限超過のバッチ処理と返金処理のユースケースを定義し、invoice BC を完結させる | Copilot | v1.1.0 | 精算フローの業務完全性を確保し、実運用に耐えうるシステムにできる |

## 次イテレーション（v1.1.0）への引き継ぎ

- **v1.0.0 リリース完了**: US01〜US18（64 SP）が全実装完了。`git tag v1.0.0` でリリースタグを付与する。
- **品質ベースライン**: backend 506 テスト Green、SonarQube Quality Gate PASS（new_violations: 0）、カバレッジ 89.2% 以上を維持条件とする。
- **既存 Code Smell 10 件**（record 化推奨）は `QuoteId`・`BookingId`・`ShipperId` 等。v1.1.0 の Refactor サイクルで対応する。
- **実績ベロシティ**: IT1: 10 / IT2: 10 / IT3: 12 / IT4: 13 / IT5: 11 / IT6: 8 → 平均 **10.7 SP**。次リリース計画のベロシティ基準として使用する。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-04-03 | IT6 ふりかえりを作成 | Copilot |
