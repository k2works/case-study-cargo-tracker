---
title: IT4 開発成果物レビュー - 経路連携・予約確定
description: IT4（US06/US10/US11/US12/US13）実装のマルチパースペクティブレビュー（xp-programmer/tester/architect/technical-writer/user-representative）
published: true
date: 2026-07-22T00:00:00.000Z
tags: review, development, it4, booking, routing, rust
---

# IT4 開発成果物レビュー - 経路連携・予約確定

## レビュー対象

- domain-booking（BookingStatus 状態機械・Cargo 状態遷移メソッド・Notification/NotificationPort・SelectedRouteView ACL）
- app-booking（BookingLifecycleService: 状態遷移＋通知記録）
- app-routing（RouteAdjustment・plan_routes_adjusted・confirm_route の期限超過拒否/候補同一性照合・cargo_type SQL 絞り込み）
- infra-persistence（SqlxNotificationRepository・SqlxSelectedRouteView・cargo upsert・voyage search CTE 化）
- interface-web（予約詳細実画面化・状態別ボタン・assign-routing/notify-route/confirm/revert/cancel/route-adjust）
- テスト（domain 状態機械・app mock・infra 統合・HTTP フロー）
- 設計反映（domain-model/data-model/ui_design）

## 総合評価

IT4 の実装は予約状態機械の凝集（`Cargo` の `&mut self` 遷移メソッドと不正遷移の `Result` 拒否）、BC 独立性（`SelectedRouteView` が IT3 の `CargoSpecProvider` と対称な逆方向 ACL・`domain-booking → domain-routing` 非依存）、ヘキサゴナル境界・composition root 注入（ADR-0003）、認可の型保証（`RoleGuard<R>`）を高水準で満たす。5 視点とも「設計の骨格に問題はなく、変更を楽に安全にできる水準」と評価。一方、**(1) US10 条件調整・再算出の HTTP フローテストと `within_deadline` domain 単体テストが皆無で受入基準が未実証、(2) US11 確定後の `RouteProposed` の HTTP assert・US13 差し戻し(/revert)の HTTP テスト欠落、(3) `notify_route_to_shipper` のドメイン状態検証欠如（UI ガード依存）、(4) TOCTOU 照合の二重区切りエンコードが生成/解釈で分散し round-trip テストが無い、(5) `booking_status_label` が ui_design 付録（正典）と表記不一致** が共通・重点指摘。テスト漏れ（受入基準対応表の主張と実テストの不一致）が IT3 に続き再発した。

## 改善提案（重要度順）

### 高（IT4 クローズ前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | US10 条件調整・再算出の HTTP フローテスト（`route/adjust` で候補が再算出される・0 件時導線）と `RouteCandidate::within_deadline` の domain 単体テストを追加 | route_flow_test.rs / route.rs | tester | US10 受入 2/3 がハンドラ実装済みだが web/domain レベルで未実証。受入基準対応表の主張と実テストが不一致（IT3 再発） |
| 2 | US11 確定後に予約が `RouteProposed` になる HTTP assert・US13 差し戻し(/revert)の HTTP フローテストを追加 | booking_flow_test.rs | tester | US11 受入 3・US13 受入 4 が HTTP で未実証（route_confirm の状態遷移結果を assert していない） |
| 3 | `notify_route_to_shipper` にドメイン状態検証（`RouteProposed` でのみ通知可）を追加し、UI ガード依存を解消 | app-booking/lib.rs:162 | programmer | US12 の不変条件がドメインに無く UI ボタン出し分けのみに依存。API/別画面から不正状態で通知が飛びうる |
| 4 | `booking_status_label` を ui_design.md 付録ステータス対応表（正典）と統一（仮予約/経路提案済/確認済＋「（コード）」併記、または正典側をドメイン用語に更新） | interface-web/lib.rs:206 / ui_design.md | technical-writer | UI 表記の Single Source と実装ラベルが割れている（仮受付 vs 仮予約 等） |

### 中（対応推奨・一部 IT5）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 5 | TOCTOU の `expected_voyages_list` エンコード/デコードを 1 ヘルパーに集約し round-trip テストを追加 | interface-web/lib.rs:1045,1176 | programmer | 安全機構の正しさが生成側/解釈側の二重区切り規約の暗黙一致に依存。片方変更で黙って壊れる |
| 6 | 通知宛先（`route-designer@…`）の定数化・宛先解決の責務分離。荷主通知が consignee 宛先である点の US12 仕様突合 | app-booking/lib.rs:131,182,203 | programmer | ハードコード重複。荷主 ≠ 荷受人で宛先の妥当性に曖昧さ |
| 7 | 確定/差し戻し/キャンセルの確認ダイアログ（single-click 誤操作防止） | booking_show.html | user-rep | 状態を確定的に変える操作が single-click で誤操作即事故 |
| 8 | US10 0 件時の「荷主との条件協議依頼」を文言のみでなく実導線（通知記録・遷移）にする | route_design.html / app | user-rep, tester | US10 受入 4 が案内文のみで実処理が無い |
| 9 | 期限超過候補（⚠）のラジオ選択不可化 or 明示確認（app 層は Try#3 で拒否済だが UI で選べる） | route_design.html | user-rep | 誤確定防止を UI にも反映（app 拒否＝422 で UX が不親切） |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| 10 | `BookingStatus` に述語メソッド（`is_preliminary()` 等）・`label()` を持たせ、web の文字列マジック比較を排除 | value_objects.rs / interface-web | programmer, technical-writer |
| 11 | `selected_route_view` の到着予定日・transit_days が UTC 基準で境界日ずれの可能性 | selected_route_view.rs:72 | programmer |
| 12 | booking_show に到着期限を表示（荷主承認判断の材料） | booking_show.html | user-rep |
| 13 | `find_all`/`search` の後段 `load_voyage` ループ N+1（CTE は ID 絞り込みのみ最適化） | voyage_repository.rs | programmer |

## 業務視点の指摘（user-representative・多くは IT5/後続スコープ）

| # | 指摘 | 対応方針 |
|---|------|----------|
| A | 料金概算が項目ごと「-」で荷主の承認判断に不十分（US12 受入 2） | 費用は Billing/Estimation 連携（後続）。IT3/IT4 で合意済みスコープ。連携タイミングを IT6+ で検討 |
| B | 荷主のルート承認状態が未記録（承認/変更依頼の区別なし） | US13 の承認は「予約確定」操作で表現。明示的な承認状態記録は後続で検討 |
| C | 経由地追加の再算出が未対応（US10 受入 2 の一部） | 探索アルゴリズム改修を要すため後続に切り出し（計画リスク表で合意） |

## ADR 起票推奨（architect）

- **ADR-0004: BC 跨ぎ書き込みの一貫性方針**（最優先）: `route_confirm` が confirm_route（Routing 確定経路保存）→ propose_route（Booking 状態遷移）を別トランザクションで逐次実行し、部分失敗で中間状態が残る。BC 独立を保つ以上正しい選択だが、`save` の upsert 冪等性で収束する性質を含め明文化する
- **ADR-0005: 予約状態機械の遷移ルール**: `Cargo` に閉じ込めた遷移集合と不正遷移拒否（`InvalidStatusTransition`）を記録
- **通知の「送信＝記録」限定**: 実配信（メール/SMS）を後続とする意図的負債として記録

## 高優先度指摘への対応方針

| # | 指摘 | 対応方針 |
|---|------|----------|
| 1-4 高 | US10 web/domain テスト・US11/US13 HTTP assert・US12 ドメイン検証・ラベル正典統一 | **IT4 クローズ前に対応**。受入基準を実証し、ドメイン不変条件と UI 正典を揃える |
| 5-6 中 | TOCTOU エンコード集約・通知宛先 | 5（round-trip テスト）は本 IT で対応可、6 は IT5 に整理 |
| 7-9 中 | 確認ダイアログ・0 件協議導線・期限超過 UI | UX 改善として IT5 優先検討（B の誤確定防止と一体） |
| A-C 業務 | 料金・承認状態・経由地追加 | スコープ合意（後続）。IT5-6 で手当て |

## エージェント別サマリー

- **xp-programmer**（中 3 / 低 3・品質 4.3/5）: 状態遷移の対称性・BC 逆方向 ACL・`lifecycle_action` の DRY を高評価。US12 ドメイン不変条件欠如・TOCTOU エンコード二重管理・宛先ハードコードを指摘
- **xp-tester**（重大 1 / 中 2 / プロセス 1）: domain 状態機械・TOCTOU/期限超過境界テストは模範的。US10 web/domain テスト皆無・US11/US13 HTTP assert 欠落・対応表と実テスト名の体系的乖離（IT3 再発）を指摘
- **xp-architect**（ADR 3）: BC 独立・ヘキサゴナル境界・状態機械の凝集は健全。route_confirm の 2 サービス跨ぎ書き込みを ADR-0004 として最優先明文化を推奨
- **xp-technical-writer**（要対応 1）: rustdoc・設計反映は高品質。`booking_status_label` が ui_design 付録（正典）と表記不一致（仮受付/経路提案中/予約確定 vs 仮予約/経路提案済/確認済・コード併記なし）
- **xp-user-representative**（高 3 / 中複数）: 状態機械とロール分担は業務に整合。料金概算欠落・single-click 誤操作・0 件協議導線の文言のみを指摘

## 更新履歴

| 日付 | 更新内容 |
|------|---------|
| 2026-07-22 | 初版作成（5 エージェント並列レビュー統合） |
