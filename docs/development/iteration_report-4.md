---
title: イテレーション 4 完了報告書 - 経路連携・予約確定
description: IT4（US06/US10/US11/US12/US13）の達成度・指標・テスト結果・レビュー・評価
published: true
date: 2026-07-22T00:00:00.000Z
---

# イテレーション 4 完了報告書

## エグゼクティブサマリー

| 項目 | 内容 |
|------|------|
| **イテレーション** | 4（経路連携・予約確定） |
| **期間** | 2026-07-22（実績・単日集中） |
| **局面** | 中盤（インサイドアウト） |
| **計画 SP / 実績 SP** | 14 / 14 |
| **達成率** | 100%（機能スコープ） |
| **テスト** | domain-booking 22 + app-booking 9 + app-routing 12 + domain-routing 29 + infra 統合 + HTTP フロー（route 11・booking 12）＝ワークスペース 168 テスト全 green |
| **カバレッジ** | domain/app 4 クレート 86.40% lines（CI ゲート 85% 突破） |
| **主要成果** | 予約状態機械（RouteDesigning 追加・遷移メソッド）＋経路設計依頼→紐付け→通知→確定/差し戻し/キャンセルの一貫フロー＋BC 独立の逆方向 ACL＋IT3 Try 全返済＋ADR-0004/0005 起票 |

## 1. イテレーション概要

### 1.1 目的と背景

IT4 は中盤局面（インサイドアウト）の 3 番目のイテレーションであり、Booking Context の予約ライフサイクルを状態機械として構築することを主眼とした。着手前調査で `BookingStatus` に「経路設計中（RouteDesigning）」が欠落し `Cargo` 集約に状態遷移メソッドが皆無であることが判明したため、これを inside-out でドメイン層から作り込み、経路設計依頼（US06）・条件調整再算出（US10）・確定経路の予約紐付け（US11）・荷主通知（US12）・予約確定（US13）を予約詳細／経路設計画面で一貫成立させた。あわせて IT3 ふりかえりの Try（cargo_type 絞り込み・誤確定防止・TOCTOU・CTE 化）を全返済した。

### 1.2 スコープ

| ID | ユーザーストーリー | SP | 結果 |
|----|-------------------|----|------|
| US06 | 予約情報を経路設計者に引き渡す | 2 | 完了 |
| US10 | 経路条件を調整して再算出する | 5 | 完了（経由地追加は後続に切り出し） |
| US11 | 経路情報を予約に紐付ける | 2 | 完了 |
| US12 | 確定経路を荷主に通知する | 2 | 完了（料金概算は「-」・費用連携は後続） |
| US13 | 予約を確定する | 3 | 完了 |
| **合計** | | **14** | |

## 2. 達成状況

### 2.1 ストーリー別受入条件

- **US06**: 予約情報確認、経路設計依頼で `Preliminary → RouteDesigning` 遷移、経路設計者への通知記録、不備時の遷移不可（不正遷移拒否）を実装・実証。
- **US10**: 現制約確認、期限延長・貨物種別変更での再算出、調整後候補の再提示、0 件時の協議依頼導線（文言）を実装・実証。
- **US11**: 確定経路と予約番号の確認、紐付け操作（route/confirm で `RouteDesigning → RouteProposed`）を実装・実証。
- **US12**: 紐付け経路の確認、通知内容（経由港・所要日数・到着予定日・料金概算「-」）、荷主への通知記録を実装・実証。ドメイン層で `RouteProposed` 限定の不変条件を担保。
- **US13**: 予約内容・選択ルート確認、確定（`RouteProposed → Confirmed`）、追跡番号発行依頼通知、ルート変更差し戻し、キャンセル＋確認通知を実装・実証。

### 2.2 局面移行の一貫性

IT2/IT3 と同一の中盤（インサイドアウト）局面のため、ドメイン層→infra→app→interface の積み上げ順・Red-Green-Refactor・`RoleGuard<R>` 認可・composition root 注入（ADR-0003）・共有カーネル `Location` を一貫踏襲。BC 独立の逆方向 ACL（`SelectedRouteView`）を IT3 の `CargoSpecProvider` と対称に確立した。

## 3. 技術的成果

### 3.1 実装（レイヤー別）

- **domain-booking**: `BookingStatus::RouteDesigning` 追加、`Cargo` 状態遷移メソッド（5 種）、`BookingError::InvalidStatusTransition`、通知ドメイン（`NotificationType`/`Notification`/`NotificationPort`）、`SelectedRouteView`/`SelectedRouteSummary` ACL。
- **app-booking**: `BookingLifecycleService`（状態遷移＋通知記録・US12 のドメイン状態検証を含む）。
- **app-routing**: `RouteAdjustment`・`plan_routes_adjusted`（US10）、`confirm_route` の期限超過拒否（Try#3）・候補同一性照合（Try#4）、`calculate_for_spec` の cargo_type SQL 絞り込み（Try#2）。
- **infra-persistence**: `SqlxNotificationRepository`・`SqlxSelectedRouteView`、`CargoRepository::save` の upsert 化、voyage search の CTE 化（Try#5）、`notification` マイグレーション。
- **interface-web**: 予約詳細の実画面化（状態別ボタン・選択ルート表示・正典準拠ラベル）、`assign-routing`/`notify-route`/`confirm`/`revert`/`cancel`/`route-adjust` ハンドラ、`route_confirm` の US11 遷移・TOCTOU の expected_voyages_list。

### 3.2 アーキテクチャ上の意思決定（ADR）

- **ADR-0004**: BC 跨ぎ書き込み（Routing 確定経路保存＋Booking 状態遷移）を単一トランザクションで束ねず、各 BC 内整合＋冪等リトライで結果整合に収束させる。
- **ADR-0005**: 予約状態遷移を `Cargo` 集約の `&mut self` メソッドに閉じ込め、不正遷移を `Result::Err` で拒否する。

## 4. 品質指標

| 指標 | 実績 |
|------|------|
| テストカバレッジ（domain/app 4 クレート） | 86.40% lines（CI ゲート 85% 突破） |
| 全テスト | ワークスペース 168 テスト全 green |
| ビルド・Lint | clippy `-D warnings` クリーン・fmt 準拠・CI 稼働 |
| ベロシティ | 14 SP（IT1=16 → IT2=11 → IT3=11 → IT4=14、計画ラインと一致） |

### コミット内訳（IT4 分）

- feat: 4（domain-booking 状態機械・通知ドメイン＋repo・app/infra/web 連携・cargo upsert）
- test/fix: 1（レビュー高優先度対応）
- refactor: 1（voyage search CTE 化）
- docs: 計画/レビュー/進捗/ふりかえり/設計反映/ADR 2 件

## 5. レビュー結果

`developing-review`（5 エージェント並列）を実施し、統合レポートを [it4_development_review_20260722.md](../review/it4_development_review_20260722.md) に記録（高 4 / 中 5 / 低 4 + 業務 3 + ADR 3）。高優先度 4 件（US10 web/domain テスト・US11/US13 HTTP assert・US12 ドメイン検証・ラベル正典統一）はクローズ前に対応。ADR-0004/0005 を起票。中・業務指摘（確認ダイアログ・0 件協議導線・料金概算・経由地追加）は IT5/後続の Try に整理した。

## 6. 課題と残作業

- **受入基準対応表と実テストの乖離が IT3-4 で再発**: 対応表の想定テスト名と実テストの grep 突合を closing-iteration 品質ゲートに追加する（Try#1）。
- **BC 跨ぎ書き込みの中間状態**: 冪等収束で許容（ADR-0004）。将来はイベント＋アウトボックスで明示化。
- **TOCTOU エンコードの二重管理**: ヘルパー集約＋round-trip テスト（Try#5）。
- **UX 負債**: 状態変更操作の確認ダイアログ・期限超過候補の UI 選択不可化・0 件協議導線の実処理化（Try#2-4）。

## 7. 次イテレーション（IT5）への引き継ぎ

- **IT5 スコープ**: US14（追跡番号発行）・US15/US16（荷役・引取記録）・US17（貨物状態手動更新）で Release 1.0 MVP 完成。`Confirmed → TrackingIssued` 以降の遷移と Tracking Context（スケルトン）本格実装。
- **通知実配信・料金連携・承認状態記録・経由地追加**は後続で手当て。
- **infra-eventbus 骨格**を IT5 で BC 跨ぎイベント化とあわせて進める。

## 更新履歴

| 日付 | 更新内容 |
|------|---------|
| 2026-07-22 | 初版作成（IT4 クローズ） |
