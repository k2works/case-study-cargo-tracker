---
title: イテレーション 7 完了報告書
description: IT7（見積 US01・料金算出 US21・法人割引 US22・精算 US23・Estimation/Billing 新設・Phase 4 完了・Release 1.0）の成果・品質指標・課題
date: 2026-07-29T00:00:00.000Z
---

# イテレーション 7 完了報告書

## エグゼクティブサマリー

Phase 4（見積・料金計算・精算）として、輸送見積作成（US01）・輸送料金算出（US21）・法人割引適用（US22）・精算処理（US23）を終盤アウトサイドインの TDD で完成させました。Estimation Context（`Estimate` 集約・`RouteCandidate` 永続化）と Billing Context（`Invoice` 集約・`FreightCalculationService`・`MoneyAmount`）の 2 BC をゼロから新設し、既存集約（Booking/Routing/Shipper）と見積〜精算の業務シナリオで結合しました。計画 18 SP を 100% 消化し、RSpec 397 examples 0 failures・全体カバレッジ 96.01%・SonarQube Quality Gate PASS・CI success を達成。マルチパースペクティブレビュー（5 視点）の高優先 6 件をクローズ前に全対応しました。本イテレーションで全 4 Phase（US01-US27）が完了し、**Release 1.0**（MVP 完成）を発行します。

## 達成状況

| US | 概要 | SP | 状態 |
|:---|:-----|:--|:-----|
| US01 | 輸送見積を作成する（ルート候補・概算料金・危険物申告） | 5 | 完了 |
| US21 | 輸送料金を算出する（距離係数×重量×貨物種別係数+燃油+消費税10%） | 5 | 完了 |
| US22 | 法人割引を適用する（0〜30%・個人非適用・割引根拠表示） | 3 | 完了 |
| US23 | 精算を処理する（入金確認→CONFIRMED→予約 SETTLED 同期） | 5 | 完了※ |
| **計** | | **18** | **100%（実績 18 SP）** |

※US23 受入基準5「支払期限超過の未払い通知」は OVERDUE 遷移（ドメイン）まで実装済みで駆動バッチが未実装、US21 受入基準6「料金調整（減額・補償）」は未実装。IT8 引き継ぎ。

## 技術的成果

- **Estimation Context の新設**: `Estimate` 集約（UUID 採番・origin≠destination・重量正）・`RouteCandidate`（永続値オブジェクト）・`EstimateStatus`。Routing の一時候補を ACL 変換して永続化（ADR-0004 決定4）。概算料金は Billing の `FreightCalculator` 公開 API 経由。
- **Billing Context の新設**: `FreightCalculationService`（距離係数×重量×貨物種別係数 GENERAL 1.0/HAZARDOUS 1.8/REFRIGERATED 1.5 → 割引 → 燃油サーチャージ → 消費税 10%）・`Invoice` 集約（発行→入金確認→期限超過・PENDING ガード）・`MoneyAmount`/`DiscountRate`/`Surcharge`/`PaymentStatus`・`PaymentGatewayPort`。
- **精算のイベントコレオグラフィ**: `invoice_created`（精算書発行→荷主通知）・`invoice_settled`（精算完了→荷主通知・予約 SETTLED 同期）をコミット後発行（ADR-0002）。
- **5 テーブル新設**: estimates/route_candidates/invoices/invoice_line_items/payments（invoices の booking_id UK で二重請求防止・lock_version 楽観ロック・base_amount/shipper_id/surcharge を永続化）。
- **負債返済**: T35（荷役冪等キーの DB unique index・並行 POST の TOCTOU 最終防衛）。
- **BC 独立性**: 非循環 DAG・全越境が公開 API + ADR-0003 越境識別子・Packwerk privacy ゼロ違反。

## 品質指標

| 指標 | 結果 | 目標 | 判定 |
|:--|:--|:--|:--|
| RSpec | 397 examples 0 failures | 全 green | ✅ |
| 全体カバレッジ（Line） | 96.01% | 80% 以上 | ✅ |
| 新規コードカバレッジ | 94.4% | 80% 以上 | ✅ |
| SonarQube Quality Gate | PASS | PASS | ✅ |
| 重複率 | 0.0% | 3% 未満 | ✅ |
| CI（Backend CI） | success | success | ✅ |
| RuboCop / Brakeman / bundler-audit / Packwerk | 0 / 0 / 0 / 0 | 0 | ✅ |

## レビュー結果

マルチパースペクティブレビュー（5 視点）を実施。高優先度 6 件をすべてクローズ前に対応:

- **H1（tech-writer + user-rep 収束）**: 請求明細のサーチャージ欠落 → 燃油サーチャージ行・税抜小計を追加し合計を検算可能に。
- **H2/H3（architect）**: SettleInvoice の決済呼び出し順序・partial-apply → 状態ガードを前へ・同期失敗を検知。
- **H4（programmer + architect 収束）**: shipper_id 消失 → 永続化・実荷主宛通知。
- **H5（programmer）**: 割引率上限超過の 500 → 上限クランプ。
- **H6（tester）**: 通知の負同値未検証 → テスト追加。

中・低の一部（採番・丸め方針・未払い通知バッチ・料金調整）は IT8 へ引き継ぎ。詳細は [IT7 実装レビュー](../review/IT7実装_review_20260729.md)。

## 課題と残作業（IT8 引き継ぎ）

- **US23 未払い通知バッチ（T41）**: OVERDUE 遷移を駆動するスケジューラが未実装。
- **US21 料金調整（T42）**: 例外発生時の減額・補償費用の明細追加が未実装。
- **Try**: T38（明細検算 DoD）・T39（複数集約更新の順序型化）・T40（越境識別子永続化 DoD）・T36（例外解決セマンティクス・IT6 から）・T37（新到着予定日構造化・IT6 から）。
- **将来**: 請求番号採番の DB シーケンス化・金額丸め方針の MoneyAmount 集約・通知の Outbox 化。

## 次イテレーション（IT8）引き継ぎ

- 全 4 Phase（US01-US27）完了。Release 1.0（MVP）発行。
- IT8 は受入基準の残（未払い通知バッチ・料金調整）と品質改善・運用を扱う位置づけ。

## 関連ドキュメント

- [イテレーション 7 計画](iteration_plan-7.md)
- [イテレーション 7 ふりかえり](retrospective-7.md)
- [IT7 実装レビュー](../review/IT7実装_review_20260729.md)
- [ドメインモデル](../design/domain-model.md)（Estimation / Billing Context）
- [データモデル](../design/data-model.md)（estimates/route_candidates/invoices/invoice_line_items/payments）
- [ADR-0004](../adr/0004-us08-route-candidate-bc-placement.md)（RouteCandidate 統合方針）
- [リリース計画](release_plan.md)

## 更新履歴

| 日付 | 版 | 変更内容 | 担当 |
|:-----|:---|:---------|:-----|
| 2026-07-29 | 初版 | IT7 完了報告書を作成 | 開発チーム |
