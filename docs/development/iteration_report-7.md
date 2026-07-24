---
title: イテレーション 7 完了報告書 - 破損紛失例外・料金算出・法人割引
description: IT7（US20/US21/US22）破損・紛失例外処理・輸送料金算出・法人割引適用の完了報告
published: true
date: 2026-07-24T00:00:00.000Z
---

# イテレーション 7 完了報告書

## エグゼクティブサマリー

| 項目 | 内容 |
|------|------|
| **イテレーション** | 7（破損紛失例外・料金算出・法人割引） |
| **期間** | 2026-07-24（実績・集中実装セッション） |
| **局面** | 終盤（アウトサイドイン） |
| **計画 SP / 実績 SP** | 13 / 13 |
| **達成率** | 100%（機能スコープ） |
| **対象ストーリー** | US20・US21・US22 |
| **主要成果** | Billing Context をスケルトンから本格実装し US21 輸送料金算出・US22 法人割引が実 PostgreSQL 上で成立。Tracking 例外を破損・紛失へ拡張し US20（紛失 escalation）が成立。**Phase 3 継続・累計 92/97 SP（95%）**。ADR-0009/0010 起票・5 視点レビュー高優先度 7 件をクローズ前返済 |

IT7 は終盤（アウトサイドイン）の第 2 イテレーションとして、既存コンテキスト（Booking・Handling・Routing・Shipper）を業務シナリオ起点で束ね、破損・紛失例外処理（US20）・輸送料金算出（US21）・法人割引適用（US22）を成立させた。新コンテキスト Billing を BC 独立で本格実装し、Tracking の `ExceptionType` を破損・紛失へ拡張した。5 視点マルチパースペクティブレビューで検出された高優先度（再算出時の charge_id 不整合バグ・例外/料金根拠の可視化欠落・受入基準×テストの穴・金額丸め未定義・割引率表示）7 件をクローズ前に全返済した。累計 92/97 SP（95%）で計画ラインと一致、ベロシティは 7 IT 連続で安定推移している。

## 1. イテレーション概要

### 1.1 目的と背景

追跡管理者・荷役作業員が破損・紛失を記録し紛失は管理職へ緊急通知できる（US20）ようにし、経理担当者が引取済予約の輸送料金を算出・確定し（US21）、法人荷主には契約割引を自動適用する（US22）。これにより Release 1.1（例外対応・請求）の請求基盤を確立し、精算（US23・IT8）の入力となる「確定した輸送料金」を作る。

### 1.2 スコープ

| ID | ストーリー | SP | 結果 |
|----|-----------|----|----|
| US20 | 破損・紛失例外を処理する | 5 | 完了（破損/紛失記録→Exception 状態・紛失は escalation_flag＋管理職通知・荷主通知・対応報告→解決） |
| US21 | 輸送料金を算出する | 5 | 完了（引取済予約→基本料金自動算定→例外調整→確定(Confirmed)・実績表示・再算出冪等） |
| US22 | 法人割引を適用する | 3 | 完了（法人は契約割引率を自動適用・個人は無割引・割引根拠を % 表示） |
| **合計** | | **13** | **全完了** |

スコープ外（後続 IT）: 精算処理（US23・IT8）、通知の実配信（メール送信）、荷役実績の料金反映・distance 実距離化、rank 一元化、dashboard 拡充。

## 2. 達成状況

### 2.1 ストーリー別受入条件

- **US20**: 追跡管理者/荷役作業員が破損・紛失（種別・場所・日時・理由）を記録→`TrackingExceptionEvent` 追加→`current_status()` が Exception。**紛失は `escalation_flag=true` で管理職（`ROLE_ADMIN`・manager@）へ EXCEPTION_ESCALATED 通知**、破損は非対象。荷主へ EXCEPTION_RAISED 通知（宛先＝荷受人）。対応報告（補償方針）で解決し EXCEPTION_RESOLVED 記録。追跡詳細に例外一覧・緊急バッジ・対応報告リンクを表示。HTTP フロー（宛先・ロールアサート）・E2E で検証。
- **US21**: 経理担当者（`ROLE_BILLING`）が「引取済（DELIVERED）」予約に対し料金算出開始→輸送実績（貨物種別・重量・距離）を根拠表示→基本料金（重量×単価＋距離×単価×貨物種別係数・円未満四捨五入）を自動算定→例外調整（減額・補償）を控除→確認して確定（Confirmed）。引取済でない予約は算出不可。再算出は既存料金 ID を再利用し冪等。HTTP フロー・E2E・単体（金額純粋関数）で検証。
- **US22**: 料金算出時に荷主種別が法人なら `ShipperDiscountProvider` ACL で契約割引率（0〜30%）を自動取得・基本料金に適用し割引後金額を算出、個人は無割引。割引根拠（率 %・基本料金・割引額）を料金詳細に表示。HTTP フロー・E2E（割引可視性）・単体で検証。割引根拠の精算書（invoice）記載は US23／IT8 で完全達成。

### 2.2 局面移行の一貫性

終盤（アウトサイドイン）の継続として、US20〜US22 を受入（HTTP/E2E）起点で設計し不足ドメインロジック（料金計算・割引・例外種別拡張）を補完した。IT3-6 の ACL パターンを踏襲し、Booking/Handling/Routing・Shipper 参照を `BookingActualsProvider`／`ShipperDiscountProvider` ACL に隔離。Tracking 例外は ADR-0006 の純粋関数導出を種別非依存で維持した。

## 3. 技術的成果

### 3.1 実装（レイヤー別）

| レイヤー | 成果物 |
|---------|--------|
| domain-billing（昇格） | `FreightCharge` 集約・`Money`(Decimal＋Currency・円未満丸め)・`DiscountRate`(0〜30%)・`ChargeStatus`(Draft/Confirmed)・`AdjustmentReason`・`ChargeAdjustment`・`DiscountLine`・`FreightChargeId`・`BillingBookingId`・`FreightChargeRepository` ポート（13 テスト） |
| app-billing（新設） | `CalculateFreightService`（算出・確定・再算出冪等・確定済み拒否）・`calculate_base_amount` 純粋関数・`BookingActualsProvider`／`ShipperDiscountProvider` ACL ポート（8 テスト） |
| domain-tracking（拡張） | `ExceptionType` に Damage/Lost 追加・`requires_escalation()`・`TrackingExceptionEvent::new` が escalation_flag を種別導出（16 テスト） |
| app-tracking（拡張） | `notify_exception_escalated` ポート・`raise_exception` で紛失時 escalation 発火（9 テスト） |
| infra-persistence | `20260930000001_it7_billing_charge.sql`（freight_charge/freight_charge_adjustment）・`SqlxFreightChargeRepository`（upsert 冪等・調整洗い替え・統合 2 テスト） |
| interface-web | `BillingRole` marker・`billing_acl.rs`（実績集約・割引率取得 ACL）・料金ハンドラ（一覧/算出/詳細/確定）・charge テンプレート 3・tracking_show に例外一覧/解決導線・navbar 料金メニュー（billing_flow 6・estimation_exception 8 テスト） |

### 3.2 テスト結果

| 種別 | 件数・結果 |
|------|-----------|
| 単体（domain/app） | domain-billing 13・app-billing 8・domain-tracking 16・app-tracking 9 ほか全 green |
| 統合（testcontainers） | billing_flow 6・estimation_exception_flow 8・freight_charge_repository 2・既存フロー全 green（回帰なし） |
| E2E（Playwright） | it7-demo 3（US20 escalation 可視性・US21/US22 料金算出→割引→確定） |
| Lint/フォーマット | clippy `-D warnings` クリーン・fmt 準拠（`+stable` 1.97.1・CI 同一ツールチェーン） |

### 3.3 ADR

- **ADR-0009**: 輸送料金（`freight_charge`）と精算書（`invoice`）の段階分割。確定した料金が精算書生成の入力。
- **ADR-0010**: `Money`／`DiscountRate` を shared-kernel へ昇格せず Billing ローカルに定義（BC 独立）。JPY 円未満四捨五入の丸め規則を明記。

## 4. 品質指標

| 指標 | 実績 |
|------|------|
| 達成率 | 100%（US20/US21/US22 全受入・機能スコープ） |
| ベロシティ | 13 SP（7 IT 連続で計画ラインと一致・安定） |
| 累計進捗 | 92/97 SP（95%） |
| BC 独立性 | 違反なし（architect 検証・domain-billing は shared-kernel のみ依存） |
| レビュー | 5 視点・高優先度 7 件クローズ前返済・中低は IT8 Try 繰り越し |

## 5. レビュー結果

5 視点マルチパースペクティブレビュー（[レポート](../review/it7_development_review_20260724.md)）で全視点がクローズ可（条件付き）と判定。高優先度 7 件をクローズ前に返済:

1. 再算出時の charge_id 不整合（404 バグ）→ 既存 ID 再利用＋確定済み拒否（programmer）
2. 割引率の生 Decimal 表示 → % 表記（technical-writer）
3. 料金詳細に輸送実績非表示 → ACL 再取得で根拠表示（user-representative）
4. 追跡詳細に例外・緊急フラグ非表示・resolve 導線なし → 例外一覧・バッジ・対応報告リンク追加（user-representative）
5. US20 破損/紛失の対応報告 HTTP テスト漏れ → 追加（tester）
6. US21 例外調整の HTTP 未実証 → 追加（tester）
7. 金額の丸め未定義 → 円未満四捨五入を Money に明示・ADR-0010 追記（programmer）

## 6. 課題と残作業（IT8 Try へ繰り越し・正直な記録）

- **通知の実配信・履歴 UI**（Try#3・IT6 から再繰り越し）: 送信＝記録に留まり実メール配信・履歴可視化 UI が未実装。
- **距離・荷役実績の料金反映**（Try#4/#7）: distance が名目スタブ（レグ×5000km）、荷役実績が料金式に未反映。US21 受入基準「荷役作業実績をもとに」を厳密には満たさない（意図的負債・ADR/コメントで可視化）。
- **rank 一元化**（Try#5・ADR-0007）: 未返済で継続。
- **per-handler の DI 整理**（Try#6・architect 指摘）: service/ACL 組立を composition root へ引き上げ。
- **US22 の精算書記載**: 割引根拠の invoice 記載は US23／IT8 で完全達成。
- **`cargo audit`／`cargo deny`**: CI で実行（ローカル未計測）。

## 7. 次イテレーション（IT8）への引き継ぎ

- **IT8 スコープ**: 精算処理（US23・Billing Context 完成）＋統合・E2E ハードニング＋非機能要件の受け入れ確認・リリース準備（Release 1.1 完成）。確定した `freight_charge` を入力に `invoice`（精算書）を生成（ADR-0009）。
- **IT8 冒頭で対処**（user-rep 条件）: 通知実配信（Try#3）・料金根拠の実データ化（Try#4）を US23 精算業務レビュー前に対処する。
- **Release 1.1 完成条件**: 見積・例外・精算の業務シナリオ受入・法人割引/紛失 escalation 検証・全テスト/E2E/パフォーマンス・`cargo audit`/`cargo deny` 緑。

## 関連ドキュメント

- [イテレーション 7 計画](./iteration_plan-7.md)
- [イテレーション 7 ふりかえり](./retrospective-7.md)
- [IT7 開発成果物レビュー](../review/it7_development_review_20260724.md)
- [ADR-0009 輸送料金と精算書の段階分割](../adr/0009-freight-charge-and-invoice-separation.md)
- [ADR-0010 Money の BC ローカル定義](../adr/0010-billing-money-value-object.md)
- [リリース計画](./release_plan.md)
