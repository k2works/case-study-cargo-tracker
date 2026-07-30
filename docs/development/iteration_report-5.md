---
title: イテレーション 5 完了報告書
description: IT5（荷役作業記録・引取作業記録・貨物状態手動更新・Tracking Context 着手）の完了報告
date: 2026-07-30
---

# イテレーション 5 完了報告書

## エグゼクティブサマリー

IT5 は US15（荷役作業記録）・US16（引取作業記録）・US17（貨物状態手動更新）の 10SP を達成率 100% で完了した。Handling Context の荷役妥当性検証（`isValidFor` デシジョンテーブル）をドメイン層から固め、荷役登録がイベント連携で Tracking の貨物状態と Booking の経路状態（MISROUTED）へ波及する基幹連携を完成させた。IT4 の Try 5 件（US12 荷主宛先是正・ADR-009・leg NOT NULL 化・SonarQube 整備・Tracking 着手）を返済し、ローカル SonarQube の Quality Gate を初めてクローズ品質ゲートに組み込んだ。中盤（インサイドアウト）局面はこれで完了し、IT6 から終盤（アウトサイドイン）へ移行する。

## 概要

| 項目 | 内容 |
| :--- | :--- |
| 期間 | 2026-09-21 〜 2026-10-04（計画 Week 9-10） / 2026-07-30（実績記録） |
| 目標 SP / 実績 SP | 10 / 10（達成率 100%） |
| 対象ストーリー | US15・US16・US17 |
| コミット数 | 11（実装 6 + 計画/レビュー/同期 5） |
| 累計 SP | 62 / 81 |
| Phase 3 進捗 | 10 / 21 SP（48%） |

## 達成状況

| ID | ストーリー | SP | 状態 | 備考 |
| :--- | :--- | :--: | :--- | :--- |
| US15 | 荷役作業を記録する | 5 | 完了 | 追跡番号で貨物特定・isValidFor 検証（警告 / MISROUTED）・貨物状態自動更新・荷主通知。登録は種別×日時で冪等 |
| US16 | 引取作業を記録する | 3 | 完了（一部次 IT） | 荷受人確認 + 通関 CLEARED 前提・CLAIMED 遷移・精算開始点イベント発行。確認コードの永続化は IT6 引き継ぎ |
| US17 | 貨物状態を手動更新する | 2 | 完了 | 出港（→輸送中）・入港（→引取待ち）等の輸送イベントを記録。CLAIM は荷役経路限定（迂回防止）。履歴記録・荷主通知 |

## 技術的成果

- **Domain**: `HandlingActivity`（`isValidFor` デシジョンテーブル・test.each 全分岐網羅）・`HandlingType`（Voyage 必須 / MISROUTED 判定を内包）・`HandlingVoyageNumber`・`CargoSnapshot` / `LegSnapshot`、`TrackingActivity`（NOT_RECEIVED 初期状態・イベント時系列から状態導出・重複冪等・UN/LOCODE / 日時のドメイン検証）。
- **Application**: `RegisterHandlingActivityService`（冪等・コミット後副作用の try 分離）・`CustomsDeclarationService`（通関申告の登録 / 状態更新）・`TrackCargoService`（発行イベントでの作成・荷役イベント購読・手動更新ホワイトリスト）・`ShipperContactAcl`（US12 是正）。
- **イベント連携（ADR-005/009）**: `TrackingNumberIssuedEvent` → NOT_RECEIVED 作成、`HandlingActivityRegisteredEvent` → Tracking 状態自動更新 + Booking MISROUTED 反映（冪等リスナー・失敗の非波及を統合テストで検証)、`CargoClaimedEvent`（精算開始点。Billing 購読は IT7）。
- **Infrastructure**: migration 005（leg 時刻 NOT NULL 化）・006（handling_activity・customs_declaration・cargo.routing_status）・007（tracking_activity・tracking_handling_event）。
- **Presentation / UI**: `/handling` 一覧・`/handling/new` 登録（警告 / MISROUTED 表示）、`/tracking` 入力・`/tracking/{tn}` 詳細（タイムライン・手動更新）、`/bookings/{id}/notify` 通知内容確認画面（宛先=荷主・経由港・所要日数・到着予定日・料金概算）、予約詳細の「追跡を表示」導線、TRACKER のナビ到達性是正。
- **設計同期**: ADR-009 起票（コミット後副作用方針）、ADR-008 追記（共有 DB 直読の範囲）、domain-model / data-model / ui_design を IT5 実績へ同期（`HandlingVoyageNumber` 命名統一・`CargoSnapshotAcl` の ACL Ports 登録を含む）。

## 品質指標

| メトリクス | 実績 | 目標 | 判定 |
| :--- | :--- | :--- | :--- |
| `npm run verify` | 53 files / 376 tests green | 全 green | PASS |
| lint / typecheck / dependency-cruiser | no violation | 全 green | PASS |
| カバレッジ（全体 statements） | 94.4% | 75% | PASS |
| CI（Lint/Typecheck/Arch/Test・E2E） | success（run 30512431440） | success | PASS |
| SonarQube Quality Gate | **PASS**（新規カバレッジ 92.5%・重複 0.41%・新規違反 0） | PASS | PASS |

> IT4 まで未整備だったローカル SonarQube を `operating-qt`（sonar-local:check）で品質ゲートに組み込んだ（IT4 Try T5 返済）。スキャンで検出された新規指摘 7 件（void 演算子・Set 化・at(-1) 等)はクローズ内で解消した。

## レビュー結果

XP 5 視点のマルチパースペクティブレビューを実施（[レビューレポート](../review/IT5実装_review_20260730.md)）。

主なクローズ内対応（12 件）:

- 手動更新経路からの CLAIM 迂回を遮断（US16 不変条件の複数経路担保）、荷役登録を冪等化（二重精算開始イベント防止）。
- TRACKER のナビ到達性欠落を是正し、手動更新に出港・入港イベントを追加（US17 の存在意義の回復。7 状態すべて到達可能に）。
- completionTime / UN・LOCODE のドメイン検証（Invalid Date による冪等判定破綻の封止）、emit と通知の try 分離、冪等スキップの誤成功表示是正ほか。

次 IT 引き継ぎ（8 件）: 通関申告の集約化、通知ポート統一と notification_record の所有整理、イベント契約型の共有、追跡レコード遅延作成の競合対策、荷受人確認の永続化、業務 UX 改善（登録前確認・状態確認導線・追跡番号検索）等。詳細は [ふりかえり](retrospective-5.md) の Try に反映済み。

## 課題と残作業

- 通関ステータス画面（`/tracking/{tn}/customs`）は計画どおり IT6 スコープ。通関状態の変更は現状コマンド（スタブ ACL / フィクスチャ）経由のみ。
- ONBOARD_CARRIER / AWAITING_CLAIM は手動更新でのみ到達（自動導出は IT6 の例外処理・スケジュール連携で検討）。
- US10 経由地追加の再算出・共有 DB 直読の契約テスト（IT4 Try T4）は引き続き IT6 以降の判断事項。

## 次イテレーション（IT6）への引き継ぎ

- スコープ: US18（追跡照会・公開ページ・htmx ポーリング・5SP）、US19/US20（例外処理・6SP）。**局面は終盤（アウトサイドイン）へ移行**。ベロシティ実績（10SP）に対し残 19SP（IT6=11 / IT7=8）は計画どおりで再調整不要。
- ふりかえり Try 6 件（経路×コマンドマトリクス、ナビ整合の自動検証、通関集約化、通知ポート統一、イベント契約型、遅延作成の冪等化）を IT6 計画へ反映する。

## 関連ドキュメント

- [イテレーション 5 計画](iteration_plan-5.md) / [ふりかえり](retrospective-5.md)
- [IT5 実装レビュー](../review/IT5実装_review_20260730.md)
- [ADR-009 コミット後副作用](../adr/009-post-commit-side-effects.md)
- [リリース計画](release_plan.md)
