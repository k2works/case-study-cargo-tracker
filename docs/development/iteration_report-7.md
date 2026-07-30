---
title: イテレーション 7 完了報告書
description: IT7（料金算出・法人割引・精算処理）の完了報告。Phase 4 完了・Release 1.0 到達
date: 2026-07-30
---

# イテレーション 7 完了報告書

## エグゼクティブサマリー

IT7 は US21（輸送料金算出）・US22（法人割引）・US23（精算処理）の 8SP を達成率 100% で完了し、**Phase 4（請求・精算）を完了、Release 1.0 に到達した**。これにより全 27 ユーザーストーリー（81SP）が完了し、予約 → 経路設計 → 荷役 → 追跡 → 例外処理 → 精算という業務フロー全体が一気通貫で動作する。IT6 のふりかえり Try 6 件（fail-closed 認証・通知本文/型化・CUSTOMS_HOLD 冪等キーほか）をすべて返済し、Billing Context を 7 番目の BC として BC 独立性を保って新設した。計画・受入・検証をメイン、実装を Opus エージェント（6 グループ）とする分業でプロジェクトを完走した。

## 概要

| 項目 | 内容 |
| :--- | :--- |
| 期間 | 2026-10-19 〜 2026-11-01（計画 Week 13-14） / 2026-07-30（実績記録） |
| 目標 SP / 実績 SP | 8 / 8（達成率 100%） |
| 対象ストーリー | US21・US22・US23 |
| コミット数 | 12（実装 6 + ADR/レビュー/同期 6） |
| 累計 SP | 81 / 81（**全ストーリー完了**） |
| Phase 4 進捗 | 8 / 8 SP（**完了・Release 1.0**） |
| 体制 | 実装は Opus エージェント 6 グループへ委譲（うち 1 グループは中断を検証回収） |

## 達成状況

| ID | ストーリー | SP | 状態 | 備考 |
| :--- | :--- | :--: | :--- | :--- |
| US21 | 輸送料金を算出する | 3 | 完了 | 引取済（DELIVERED）の予約に対し輸送実績（経路・重量・種別・荷役実績・未解決例外）表示・基本料金自動計算・例外調整（加算/減額）・確定 |
| US22 | 法人割引を適用する | 2 | 完了 | 法人は契約割引率 0〜30% を自動適用、割引根拠（割引率・基本料金・割引後）を明細化。個人は割引なし |
| US23 | 精算を処理する | 3 | 完了（一部運用） | 精算書発行（本文=金額・期限・支払案内）・入金確認（スタブ決済）→ CONFIRMED + cargo SETTLED・OVERDUE 判定と未払い通知。実配信・実決済は運用フェーズ |

## 技術的成果

- **Domain（新 Billing Context）**: `Invoice` 集約（消費税10%込みの計算・markOverdue 初回のみ通知・遷移規則）・`Money`（decimal.js・非負・ROUND_HALF_UP）・`DiscountRate`（0〜30%・applyTo）・`InvoiceLineItem`・`PaymentStatus`・`FreightCalculator`（距離係数×重量×種別係数）。`discount_rate` カラム永続化で集約状態の再導出を排除（レビュー H1 対応）。
- **BC 連携（ADR-005/008/009）**: `BillingSnapshotAcl`（cargo×shipper×leg×例外の参照専用直読・BC 独立性維持）・`PaymentGatewayPort`（スタブ ACL）・`CargoClaimedEvent`/`PaymentConfirmedEvent`（契約を shared/contracts で共有）。Cargo の markInTransit/markDelivered/settle と冪等リスナーで BookingStatus を完結（TRACKING_ISSUED→IN_TRANSIT→DELIVERED→SETTLED）。
- **IT6 Try 返済**: 認証 fail-closed 化（ADR-011・グローバル APP_GUARD + @Public）、通知の所有集約・種別 union・本文（ADR-012・NotificationRecorder）、CUSTOMS_HOLD の申告番号単位冪等、未来日ガード。
- **Infrastructure**: migration 010（invoice/invoice_line_item/payment）・011（notification_record.body）・012（tracking_exception_event.declaration_number）・013（invoice.discount_rate）・`InvoiceRepository`。
- **Presentation / UI**: 請求書一覧（未請求予約 + 発行済み）・料金算出（実績・割引根拠・調整・未解決例外警告）・請求書詳細（明細・荷主名・入金・支払状態）・入金確認、ダッシュボード「支払期限超過の請求」カード（ROLE_BILLING）。
- **設計同期**: ADR-011/012 起票、domain-model（Billing・イベント・ACL Ports）・data-model（migration 010-013）・ui_design（請求画面）・architecture_backend（認証 fail-closed）を同期。

## 品質指標

| メトリクス | 実績 | 目標 | 判定 |
| :--- | :--- | :--- | :--- |
| `npm run verify` | 74 files / 597 tests green | 全 green | PASS |
| lint / typecheck / dependency-cruiser | no violation | 全 green | PASS |
| カバレッジ（全体 statements） | 94.05% | 75% | PASS |
| E2E（Playwright） | 8 passed | success | PASS |
| CI（Lint/Typecheck/Arch/Test・E2E） | success | success | PASS |
| SonarQube Quality Gate | **PASS**（新規カバレッジ 92.1%・重複 0.44%・新規違反 0） | PASS | PASS |

### Release 1.0 リリース条件

- [x] 全テストがパス（597 tests・Playwright 8）
- [x] カバレッジ目標（ドメイン層 90% 目標・全体 94.05%・新規 92.1%）
- [x] セキュリティチェックリスト: fail-closed 認証（ADR-011）・ロール別認可（BILLING の拒否テスト追加）・CSRF・公開ページの情報露出（公開追跡の最小表示）・情報漏えい

## レビュー結果

XP 5 視点のマルチパースペクティブレビューを実施（[レビューレポート](../review/IT7実装_review_20260730.md)）。

主なクローズ内対応（11 件）: 割引率逆算による請求書復元クラッシュの解消（High・重大バグ）、paidAt 復元・入金確認の順序、ROLE_BILLING 認可拒否テストと billing 経路の fail-closed 回帰、OVERDUE 件数の動的判定、サービス単体/境界値/端数テスト、支払先案内・未解決例外警告・荷主名表示ほか。

次テイク/バッファ引き継ぎ（8 件）: 非同期リスナー例外の非捕捉と AFTER_COMMIT 構造化、BillingSnapshot 契約テスト、距離マスタ化、PaymentGatewayPort 失敗モード、@Public メソッド単位原則、料金調整の統制/部分入金、楽観ロック、ドキュメント用語集整備。詳細は [ふりかえり](retrospective-7.md) の Try に反映。

## 課題と残作業（運用フェーズ / バッファ期間）

- 通知は記録スタブ（実配信なし）、決済は常に成功するスタブ（PaymentGatewayPort）。実連携は運用フェーズ。
- 料金の距離係数は所要日数比例の暫定式（港間距離マスタ未導入）。
- AFTER_COMMIT の transaction/outbox 構造化は全 IT を通じて未返済の負債として明示計上。

## プロジェクト総括

7 イテレーション・81SP を計画どおり完走し、v0.1（予約 MVP）→ v0.5（経路設計）→ v0.8（荷役・追跡）→ v1.0（精算完成版）の 4 段階リリースを達成した。7 つの境界付けられたコンテキスト（Booking/Shipper/Routing/Tracking/Handling/Billing/Estimation）を BC 独立性を保って実装し、DDD・ヘキサゴナル・CQRS・イベント駆動の設計を TypeScript/NestJS で貫いた。総括は [リリース完了報告書](release_report-1.0.md) に記す。

## 関連ドキュメント

- [イテレーション 7 計画](iteration_plan-7.md) / [ふりかえり](retrospective-7.md)
- [IT7 実装レビュー](../review/IT7実装_review_20260730.md)
- [ADR-011 認証 fail-closed](../adr/011-fail-closed-authentication.md) / [ADR-012 通知の所有と本文](../adr/012-notification-ownership-and-body.md)
- [リリース計画](release_plan.md)
