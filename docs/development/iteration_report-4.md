---
title: イテレーション 4 完了報告書
description: IT4（経路選択・確定 US09/US10/US11/US12/US13・通知基盤 ADR-0002・Booking 状態機械）の完了報告
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 4 完了報告書

## エグゼクティブサマリー

Phase 2 の後半として、経路候補の選択・確定（US09）、条件調整による再算出（US10）、経路の貨物紐付け（US11）、確定経路の荷主通知（US12）、予約の確定・差戻し・キャンセル（US13）を中盤インサイドアウトの TDD で完成させました。あわせて `CargoItinerary`/`Leg` 値オブジェクトと `BookingStatus` の前後遷移を備えた状態機械をドメインに確立し、ドメインイベントによる通知基盤（ADR-0002）を導入しました。計画 15 SP を 100% 消化し、RSpec 239 examples 0 failures・全体カバレッジ 94% 超を達成しました。マルチパースペクティブレビューの高優先 5 件をクローズ前に対応し、Release 0.2.0（`ruby/take-1/v0.2.0`）を発行しました。

## 達成状況

| US | 概要 | SP | 状態 |
|:---|:-----|:--|:-----|
| US09 | 経路候補を選択・確定する | 3 | ✅ 完了 |
| US10 | 条件を調整して再算出する | 3 | ✅ 完了（候補ゼロ時の条件協議依頼まで実装） |
| US11 | 経路を貨物に紐付ける | 3 | ✅ 完了 |
| US12 | 確定経路を荷主へ通知する | 3 | ✅ 完了（明示送信化。料金概算表示は IT5） |
| US13 | 予約を確定・差戻し・キャンセルする | 3 | ✅ 完了 |
| **計** | | **15** | **100%** |

受入基準の充足状況（正直な評価）:

- **US09 / US11 / US13**: 受入基準を充足。候補ラジオ選択→PATCH による経路割り当て（`ROUTE_REQUESTED`→`ROUTE_PROPOSED`）、`CargoItinerary` の全置換保存・再構成、確定（`CONFIRMED`）・差戻し（`ROUTE_REQUESTED`）・キャンセル（`CANCELLED`）を system spec で green 確認。
- **US10**: 条件調整による再算出フォームと、候補ゼロ時の条件協議依頼（`CONSULTATION_REQUESTED`→営業）まで実装して充足。
- **US12**: `NotifyShipperOfRoute` の明示操作（荷主へ通知ボタン）による明示送信化で充足。ただし料金概算表示は未対応で、次 IT（IT5）に持ち越し。

## 技術的成果

- **経路の値オブジェクト（インサイドアウト）**: `Leg`・`CargoItinerary`。連結制約（`Leg[n].unload == Leg[n+1].load`）・到着時刻導出・最終脚 `unload_time` 必須の不変条件を保持。`RouteSpecification#satisfied_by?` は期限を日付単位で比較し、当日着を刈らない。
- **Booking 状態機械**: `Cargo#assign_itinerary`（`ROUTE_REQUESTED`→`ROUTE_PROPOSED`・不正時 `InvalidItineraryError`）/ `confirm`（→`CONFIRMED`）/ `back_to_routing`（差戻し→`ROUTE_REQUESTED`）/ `cancel`（→`CANCELLED`）。`BookingStatus` に BACKWARD 遷移と述語を追加。
- **通知基盤（ADR-0002）**: `DomainEvents`（`ActiveSupport::Notifications` ラップ・publish/subscribe・購読側の例外を非伝播）・`notifications` テーブル（`notifiable_id = VARCHAR(50)` の論理ポリモーフィック・pending/sent/failed）・`Shared::Public::NotificationRecorder`・`Booking::Public::NotificationWiring`・`NotificationSubscribers`。イベントはアプリケーションサービスがコミット後に発行し、ドメインは純 PORO を保つ（DIP）。
- **ドメインイベント**: `cargo_routed`（US12・`NotifyShipperOfRoute` の明示操作）/ `cargo_confirmed`（US13・追跡番号発行依頼 `TRACKING_REQUESTED`→経路設計者）/ `cargo_cancelled`（US13・`BOOKING_CANCELLED`→荷主）/ `cargo_consultation_requested`（US10・`CONSULTATION_REQUESTED`→営業）。
- **永続化**: `legs` テーブルで `CargoItinerary` を全置換保存・再構成。`cargos` に `consignee`・`routing_status` を追加。
- **UI**: 経路割り当て画面（候補ラジオ選択→PATCH route・PRG、US10 条件調整再算出フォーム、候補ゼロ時の条件協議依頼ボタン）、予約詳細（旅程表・通知送信記録表・荷主へ通知/確定/差戻し/キャンセルボタン）。

## 品質指標

| 指標 | 結果 |
|:-----|:-----|
| RSpec | 239 examples, 0 failures |
| カバレッジ | 全体 94% 超（DoD: ドメイン 85%・全体 80% を超過。ドメイン層厚い） |
| RuboCop | no offenses |
| Packwerk | validate/check green（enforce_dependencies + privacy 実効化） |
| Brakeman | Security Warnings 0 |
| bundler-audit | 0 vulnerabilities |
| CI（Backend CI） | success |
| SonarQube | Quality Gate PASS（新規カバレッジ 87.9%・重複 0.0%・違反 0・Bug 0・Vulnerability 0） |

## レビュー結果

5 視点のマルチパースペクティブレビューを実施（[レポート](../review/IT4実装_review_20260728.md)）。

- **クローズ前対応（高 5 件）**: すべてクローズ前に対応済み。
- **設計反映**: data-model / domain-model / ui_design / architecture_backend / ADR-0002 を実装に整合（7 点＋レビュー反映）。

## 課題と残作業

- **負債返済枠 T16 / T21 / T22**: 未着手。次 IT へ持ち越し。
- **architect 中優先 T24 / T25 / T26**: T24（reconstitute 分離）・T25（replace_legs 最適化）・T26（install! 冪等）は次 IT で対応。
- **US06 の通知**: 段階移行として現状は直接呼び出し。今後 ADR-0002 のイベント方式へ寄せる。
- **テスト追加**: 多区間旅程の結合 spec・悲観ロック競合 spec は次 IT。
- **US12 料金概算表示**: 明示送信化は完了したが、料金概算表示は未対応で IT5 の優先事項。

## 次イテレーション（IT5）引き継ぎ

- 持ち越しストーリーなし（IT4 スコープ完了。US12 の料金概算表示のみスコープ調整として IT5）。
- 通知基盤（ADR-0002）を活用し、US06 の直接呼び出しをイベント方式へ段階移行する。
- architect 中優先の T24 / T25 / T26、負債返済枠 T16 / T21 / T22 を IT5 計画に組み込む。
- 多区間旅程の結合 spec・悲観ロック競合 spec を追加し、状態機械と永続化の堅牢性を高める。

## 関連ドキュメント

- [イテレーション 4 計画](iteration_plan-4.md)
- [イテレーション 4 ふりかえり](retrospective-4.md)
- [IT4 実装レビュー](../review/IT4実装_review_20260728.md)
- [ADR-0002 ドメインイベントによる通知基盤](../adr/0002-domain-events-notification.md)
- [リリース計画](release_plan.md)
- [CHANGELOG](../../CHANGELOG.md)

## 更新履歴

| 日付 | 版 | 変更内容 | 担当 |
|:-----|:---|:---------|:-----|
| 2026-07-28 | 初版 | IT4 完了報告書を作成 | 開発チーム |
