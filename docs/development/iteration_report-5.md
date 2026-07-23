---
title: イテレーション 5 完了報告書 - 追跡・荷役
description: IT5（US14/US15/US16/US17）追跡番号発行・荷役／引取記録・貨物状態手動更新の完了報告
published: true
date: 2026-07-23T00:00:00.000Z
---

# イテレーション 5 完了報告書

## エグゼクティブサマリー

| 項目 | 内容 |
|------|------|
| **イテレーション** | 5（追跡・荷役） |
| **期間** | 2026-07-23（実績・集中実装セッション） |
| **局面** | 中盤（インサイドアウト） |
| **計画 SP / 実績 SP** | 14 / 14 |
| **達成率** | 100%（機能スコープ） |
| **対象ストーリー** | US14・US15・US16・US17 |
| **主要成果** | Tracking / Handling Context をスケルトンから本格実装。追跡番号発行→荷役反映→引取→手動更新の一貫フローが実 PostgreSQL 上で成立。BC 独立を 4 ACL ポートで維持。**Release 1.0 MVP 完成**。IT4 Try#1-6 全返済。ADR-0006 起票 |

IT5 は中盤（インサイドアウト）最終イテレーションとして、Cargo Tracker の第二の中核価値である「リアルタイム貨物追跡」を成立させ、Phase 2（コア輸送フロー）を完了した。domain→app→infra→interface→統合テスト→E2E の縦切りを US14-17 で貫通し、5 視点マルチパースペクティブレビューの高優先度指摘 4 件をクローズ前に返済した。累計 66/97 SP（68%）で計画ラインと一致、ベロシティは 5 IT 連続で安定推移している。

## 1. イテレーション概要

### 1.1 目的と背景

確定した予約に追跡番号を発行し（US14）、荷役作業員が荷役・引取を記録し（US15/US16）、追跡管理者が貨物状態を手動更新できる（US17）ようにすることで、荷主がリアルタイムに輸送状況を把握できる基盤を確立する。これにより予約→経路→確定→追跡→荷役→引取という Release 1.0 MVP の一貫業務フローが完成する。

### 1.2 スコープ

| ID | ストーリー | SP | 結果 |
|----|-----------|----|----|
| US14 | 追跡番号を発行する | 3 | 完了（Confirmed→TrackingIssued・追跡活動生成・荷主通知） |
| US15 | 荷役作業を記録する | 5 | 完了（受領/積込/荷降し・追跡状態自動更新・状態変更通知・ルート相違警告） |
| US16 | 引取作業を記録する | 3 | 完了（荷受人確認必須・引取済＝配送完了） |
| US17 | 貨物状態を手動更新する | 3 | 完了（状態/位置/日時更新・履歴記録・種類別通知） |
| **合計** | | **14** | **全完了** |

スコープ外（後続 IT）: 通関前提チェック（US16 の CustomsStatus・IT6）、追跡照会画面 US18（IT6）、通知の実配信、ADR-0006 の冪等再操作パス実装。

## 2. 達成状況

### 2.1 ストーリー別受入条件

- **US14**: 「予約確定」状態のみ発行可（他状態は 422）・一意採番（TRK-プレフィックス）・発行後 NOT_RECEIVED・荷主へ追跡番号通知（notification テーブルに記録・HTTP フローでアサート）。全受入基準に対応テストあり。
- **US15**: 追跡番号で貨物特定・作業種別選択・状態自動更新（RECEIVED 等）・状態変更通知・番号不存在エラー・ルート相違警告（非ブロッキング）。全受入基準に対応テストあり。
- **US16**: 引取選択時に荷受人確認フィールドを JS 出し分け＋必須化・荷受人確認で記録・引取済（CLAIMED）更新・配送完了＝精算開始条件（状態確立まで）。
- **US17**: 追跡番号指定・状態/位置/日時更新・追跡イベント履歴記録・種類別通知。

### 2.2 局面移行の一貫性

中盤最終 IT として、IT3 `CargoSpecProvider`・IT4 `SelectedRouteView` の対称 ACL パターンを踏襲した 4 ACL ポート（`ConfirmedBookingIssuer`／`TrackingReflectionPort`／`RouteCheckPort`／`TrackingNotificationPort`）で BC 独立を維持。IT6 からの終盤（アウトサイドイン）へは、本 IT で確立した Tracking/Handling 集約を業務シナリオ起点で結合する形で移行する。

## 3. 技術的成果

### 3.1 実装（レイヤー別）

| レイヤー | 成果物 |
|---------|--------|
| domain-tracking | `TrackingActivity` 集約・`TrackingNumber`/`TrackingBookingId`/`TrackingLocation`/`TrackingVoyageNumber`・`TrackingStatus`(9値)・`current_status()` 純粋関数・`TrackingActivityRepository`/`TrackingNumberGenerator` ポート |
| domain-handling | `HandlingActivity` 集約・`HandlingType`・`HandlingLocation`・`ReceiptConfirmation`・`HandlingActivityRepository` ポート・引取の不変条件 |
| domain-booking | `Cargo::issue_tracking()`（Confirmed→TrackingIssued）・`BookingStatus` 述語メソッド/`label()` |
| app-tracking | `IssueTrackingService`(US14)・`ManualTrackingUpdateService`(US17)・ACL ポート定義 |
| app-handling | `RecordHandlingService`(US15/US16)・`TrackingReflectionPort`/`RouteCheckPort` 定義 |
| infra-persistence | マイグレーション `20260902000001_it5_tracking_handling.sql`・`SqlxTrackingActivityRepository`・`SqlxHandlingActivityRepository` |
| interface-web | `tracking_acl.rs`（4 ACL アダプター）・追跡/荷役ハンドラ・テンプレート 4 種・`HandlerRole`/`TrackerRole`・`expected_voyages` モジュール |

### 3.2 アーキテクチャ上の意思決定（ADR）

- **ADR-0006 起票**: 追跡状態の純粋関数導出（`current_status()`）と、Booking→Tracking 連携の回復戦略（冪等再操作パス・監視検出・`transport_status` を Read Model キャッシュと位置づけ）を明文化。
- **ADR-0003/0004/0005 踏襲**: Arc<dyn> ブランケット実装による composition root 注入、BC 跨ぎ書き込みの逐次確定、状態機械パターン。

## 4. 品質指標

| 指標 | 実績 |
|------|------|
| 全テスト | 全 green（ワークスペース `cargo test` exit 0・domain-tracking 9 / domain-handling 9 / app-tracking 4 / app-handling 5 / HTTP フロー 5（通知アサート含む）/ ナビ 2 / E2E 4） |
| カバレッジ（IT5 新規・lines） | app-handling 94.8% / app-tracking 93.9% / domain-handling 86-92% / domain-tracking 78-89% |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠 |
| ベロシティ | 14 SP（IT1=16→IT2=11→IT3=11→IT4=14→IT5=14・安定） |
| 累計進捗 | 66/97 SP（68%）・Phase 2 完了・Release 1.0 MVP 完成 |

### コミット内訳（IT5 分・計画作成以降）

| type | 件数 |
|------|------|
| feat | 8 |
| test | 3 |
| refactor | 2 |
| docs | 8+ |
| **変更規模** | 56 ファイル・+4,403 / -152 行 |

## 5. レビュー結果

5 視点マルチパースペクティブレビュー（[IT5 レビュー](../review/it5_development_review_20260723.md)）を実施。総評は「BC 独立・ヘキサゴナル・ADR 踏襲の設計規律が一貫した高品質」。高優先度 4 件をクローズ前に対応した。

| # | 視点 | 指摘 | 対応 |
|---|------|------|------|
| H1 | tester | 通知記録（US14/15/17）が全テストレベルで未検証 | HTTP フローで notification テーブルをアサート |
| H2 | architect | ADR-0004 の Booking→Tracking 冪等収束が非対称 | ADR-0006 で回復戦略を明文化（実装は IT6・Try#2） |
| H3 | user-rep | 引取確認フィールドが HTML で必須化されていない | CLAIM 選択時に JS で required 化 |
| H4 | tech-writer | ui_design.md に手動更新導線・荷受人確認未反映 | ui_design.md に反映 |

中・低優先度（通知重複・宛先ハードコード・transport_status 二重管理・RouteCheckPort 意味分離）は IT6 の Try に計上した。

## 6. 課題と残作業

- **通知系テストの構造的弱点**: 「送信＝記録」系受入基準が 2 IT 連続で未テストのまま実装された。IT6 で DoD に「通知アサートテスト必須」を組み込む（Try#1）。
- **ADR-0006 回復パス未実装**: Booking→Tracking の中間状態は監視検出・手動回復の運用止まり。冪等再操作パスの実装は IT6（Try#2）。
- **通知の実配信・可視化**: 本 IT は記録に限定。IT6 の US18 追跡照会とあわせて対応（Try#3）。
- **dashboard 拡充・追跡番号の予約詳細表示**: 業務導線の充実は IT6（Try#6）。

## 7. 次イテレーション（IT6）への引き継ぎ

- **IT6 スコープ**: US01（輸送見積）・US18（追跡情報照会）・US19（遅延例外処理）で終盤（アウトサイドイン）へ。
- **例外イベント導入**: `TrackingExceptionEvent` を `current_status()` の末尾判定に織り込む（ADR-0006 の導出方式踏襲）。
- **通関（Handling の CustomsDeclaration）**: US16 の Claim 前提チェック（CustomsStatus=Cleared）を IT6 で実装。
- **Try 6 件**: 通知アサート DoD 化・ADR-0006 回復パス実装・通知実配信・transport_status 整理・RouteCheckPort enum 化・dashboard 拡充。

## 更新履歴

| 日付 | 内容 |
|------|------|
| 2026-07-23 | IT5 完了報告書作成 |

## 関連ドキュメント

- [イテレーション 5 計画](./iteration_plan-5.md)
- [イテレーション 5 ふりかえり](./retrospective-5.md)
- [IT5 開発成果物レビュー](../review/it5_development_review_20260723.md)
- [ADR-0006 追跡状態の純粋関数導出と Booking→Tracking 回復戦略](../adr/0006-tracking-status-derivation-and-cross-context-recovery.md)
- [リリース計画](./release_plan.md)
