---
title: イテレーション 4 ふりかえり
description: IT4（経路連携・予約確定・US06/US10/US11/US12/US13）の Keep・Problem・Try
published: true
date: 2026-07-22T00:00:00.000Z
---

# イテレーション 4 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 4（経路連携・予約確定） |
| **局面** | 中盤（インサイドアウト） |
| **計画 SP / 実績 SP** | 14 / 14（達成率 100%） |
| **対象ストーリー** | US06・US10・US11・US12・US13 |
| **テスト** | domain-booking 22 + app-booking 9 + app-routing 12 + domain-routing 29 + infra 統合（notification/cargo status/CTE）+ HTTP フロー（route 11・booking 12）＝全 green |
| **カバレッジ** | domain/app 4 クレート合計 86.40% lines（cargo-llvm-cov・CI ゲート 85% 突破） |
| **実装コミット** | feat 4 / test 1 / refactor 1 / docs 多数（ADR 2 起票） |
| **成果** | 予約状態機械（RouteDesigning 追加・遷移メソッド）を Cargo 集約に構築。経路設計依頼→紐付け→荷主通知→確定/差し戻し/キャンセルの一貫フローが実 PostgreSQL 上で成立。BC 独立の逆方向 ACL（SelectedRouteView）確立・IT3 Try 全返済・developing-review 完了 |

## Keep（継続すること）

### 技術的成功事項

- **予約状態機械の凝集**: `Cargo` 集約に `&mut self` 遷移メソッド（`request_route_design`/`propose_route`/`confirm`/`revert_to_route_designing`/`cancel`）を閉じ込め、不正遷移を `InvalidStatusTransition` で拒否した。5 メソッドが同一パターンで対称に書かれ、遷移ごとに正常系・異常系のテストが対で存在する（ADR-0005）。programmer が「TDD の産物であることが明白」と高評価。
- **BC 独立の逆方向 ACL**: `SelectedRouteView`（Booking → Routing の読み取り ACL）を IT3 の `CargoSpecProvider`（Routing → Booking）と対称に定義し、`domain-booking → domain-routing` の依存を張らずに確定経路を参照した。コンパイラ（Cargo.toml 依存宣言）で構造違反を防ぐ設計を維持。
- **IT3 Try の完全返済**: Try#2（`plan_routes` の cargo_type SQL 絞り込み）・#3（期限超過確定拒否）・#4（候補同一性照合 TOCTOU）・#5（voyage search の CTE 化）を全て実装しテストで実証した。#1（受入基準×テスト 1:1）も対応表に想定テスト名まで記載。
- **`lifecycle_action` 高階関数の DRY**: 予約状態遷移の 4 POST ハンドラでエラー→HTTP 変換の重複を共通関数に集約し、操作追加コストを下げた。
- **認可の型保証の一貫適用**: 新規ハンドラすべてに `RoleGuard<R>`（`SalesUser`/`RouteDesignerUser`）を適用し、認可書き忘れをコンパイラが防ぐ IT1 パターンを踏襲。

### プロセス的成功事項

- **計画前の 2 検証と ADR 事前識別**: `validating-iteration-plan`＋`validating-design` を着手前に実施し、`BookingStatus::RouteDesigning` の設計欠落・コマンド遷移の不整合を事前検知して計画に注記できた。ADR 起票候補も計画段階で識別済み。
- **設計欠落の当該 IT 反映**: user_story/ui_design が前提とする「経路設計中」状態が enum・domain-model に欠落していた点を、実装と同一 IT で domain-model/data-model/ui_design に反映し先行乖離を残さなかった。
- **developing-review→改善ループ**: 5 エージェント並列レビューで高優先度 4 件（US10 web/domain テスト・US11/US13 HTTP assert・US12 ドメイン検証・ラベル正典統一）をクローズ前に対応した。

## Problem（問題点）

### テスト・受入基準の課題

- **受入基準対応表の「主張」と実テストの乖離が IT3 に続き再発**: US10 条件調整の HTTP フローテストと `within_deadline` domain 単体テストが初期実装で皆無、US11 確定後の `RouteProposed` の HTTP assert・US13 差し戻しの HTTP テストも欠落していた（tester が重大指摘、クローズ前に補完）。対応表に想定テスト名を書いても、実テストの存在を機械照合する仕組みが無いため主張と実装が乖離した。
- **US12 の不変条件がドメインに無く UI ガードに依存していた**: `notify_route_to_shipper` が状態検証をせず、UI のボタン出し分けのみで不正状態の通知を防いでいた（programmer 中1、クローズ前に修正）。

### 設計・実装の課題

- **BC 跨ぎ書き込みの中間状態**: `route_confirm` が confirm_route（Routing）→ propose_route（Booking）を別トランザクションで逐次実行するため、部分失敗で「確定経路あり・予約は経路設計中」の中間状態が残りうる（architect 指摘、ADR-0004 で方針明文化・冪等収束で許容）。
- **TOCTOU 照合の二重区切りエンコードが分散**: `expected_voyages_list` の生成（テンプレート）と解釈（ハンドラ）が別箇所にあり round-trip テストが無い。安全機構の正しさが暗黙の規約一致に依存（programmer 中2、IT5 対応）。
- **ラベルとハードコードの負債**: `booking_status_label` が ui_design 正典と表記不一致だった（修正済）。通知宛先メールのハードコード重複（IT5 整理）。

## Try（次に試すこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|----------|
| 1 | closing-iteration の品質ゲートに「受入基準対応表の想定テスト名 vs 実テスト名を grep 突合」する手順を追加し、乖離を機械検知する | 開発 | IT5 クローズ時 | 対応表と実テストの乖離（IT3-4 で 2 回再発）の根本対策 |
| 2 | 状態を確定的に変える操作（notify/confirm/revert/cancel）に確認ダイアログを追加し single-click 誤操作を防ぐ | 開発 | IT5 | 誤確定・誤キャンセル事故の防止（user-rep 高） |
| 3 | US10 0 件時の「荷主との条件協議依頼」を文言のみでなく通知記録・遷移を伴う実導線にする | 開発 | IT5 | US10 受入 4 の実処理化（現状案内文のみ） |
| 4 | 期限超過候補（⚠）を UI でラジオ選択不可化し、app 層の 422 拒否をユーザーに見せない | 開発 | IT5 | 誤確定防止の UX 改善 |
| 5 | TOCTOU の `expected_voyages_list` エンコード/デコードを 1 ヘルパーに集約し round-trip テストを追加 | 開発 | IT5 前半 | 安全機構の正しさをテストで担保 |
| 6 | `BookingStatus` に述語メソッド（`is_preliminary()` 等）・`label()` を持たせ web の文字列マジック比較を排除 | 開発 | IT5 | 状態追加時の対応漏れを型で防止 |

## 次イテレーション（IT5）への引き継ぎ

- **IT5 スコープ**: US14（追跡番号発行）・US15（荷役作業記録）・US16（引取作業記録）・US17（貨物状態手動更新）で Release 1.0 MVP を完成させる。`Confirmed → TrackingIssued` 以降の状態遷移と Tracking Context（現状スケルトン）の本格実装が始まる。
- **通知の実配信**: 本 IT では「送信＝記録」に限定。メール/SMS 実配信は `NotificationPort` の実装差し替えで後続対応。
- **料金概算（US12）・荷主承認状態の記録**: 費用は Billing/Estimation 連携（IT6+）、承認状態の明示記録は後続で検討（user-rep 業務 A/B）。
- **経由地追加の再算出（US10 受入 2 の一部）**: 探索アルゴリズム改修を要すため後続に切り出し（計画リスク表で合意）。
- **infra-eventbus 骨格**: Booking/Tracking 連携が始まる IT5 で、BC 跨ぎ処理のドメインイベント＋アウトボックス化（ADR-0004 の後続検討）とあわせて進める。

## 数値指標

| 指標 | 実績 |
|------|------|
| テストカバレッジ（domain/app 4 クレート） | 86.40% lines（CI ゲート 85% 突破） |
| 全テスト | 全 green（domain-booking 22 / app-booking 9 / app-routing 12 / domain-routing 29 / infra 統合 / HTTP フロー route 11・booking 12 ほか・ワークスペース 168 テスト） |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠・CI パイプライン稼働 |
| ベロシティ | 14 SP（IT1=16 → IT2=11 → IT3=11 → IT4=14、計画ラインと一致し安定） |

## 関連ドキュメント

- [イテレーション 4 計画](./iteration_plan-4.md)
- [IT4 開発成果物レビュー](../review/it4_development_review_20260722.md)
- [ADR-0004 BC 跨ぎ書き込み一貫性](../adr/0004-cross-context-write-consistency.md)
- [ADR-0005 予約状態機械](../adr/0005-booking-status-state-machine.md)
- [イテレーション 3 ふりかえり](./retrospective-3.md)
