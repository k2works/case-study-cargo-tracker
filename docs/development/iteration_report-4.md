---
title: イテレーション 4 完了報告書
description: IT4（経路確定・荷主通知・予約確定・追跡番号発行）の完了報告
date: 2026-07-29
---

# イテレーション 4 完了報告書

## エグゼクティブサマリー

IT4 では、IT3 で構築した経路候補算出を Booking Context の状態遷移へ接続し、経路選択・予約紐付け（US09/US11）、条件調整（US10）、荷主通知（US12）、予約確定・差戻し・キャンセル（US13）、追跡番号発行（US14）までの基幹フローを完成させた。`CargoItinerary` / `Leg`（連結制約付き値オブジェクト）、`ROUTING_IN_PROGRESS → ROUTE_PROPOSED → CONFIRMED → TRACKING_ISSUED` の状態遷移、`RouteCargoService` / `ConfirmBookingService` / `AssignTrackingNumberService`、Booking 固有の経路候補 ACL、通知記録アダプタ、経路割り当て画面を縦に接続した。

目標 16SP を 100% 達成し、Phase 2（29SP）を完了、Release 0.5 の基幹フロー E2E が green である。XP 5 視点レビューの高優先度指摘（ドメイン不変条件・POST のクライアント値信頼・越権遮断・語彙衝突・正典ドリフト）はクローズ内で対応した。

| 項目 | 内容 |
| :--- | :--- |
| 期間 | 2026-09-07 〜 2026-09-20（計画 Week 7-8） / 2026-07-29（実績記録） |
| 目標 SP / 実績 SP | 16 / 16（達成率 100%） |
| 対象ストーリー | US09・US10・US11・US12・US13・US14 |
| コミット数 | 22（実装 10 + 計画/検証/同期/レビュー 12） |
| 累計 SP | 52 / 81 |
| Phase 2 進捗 | 29 / 29 SP（完了） |

## 達成状況

| ID | ストーリー | SP | 状態 | 備考 |
| :--- | :--- | :--: | :--- | :--- |
| US09 | 経路を選択・確定する | 3 | 完了 | 候補一覧・選択、ドメイン不変条件（端点一致・期限内）で紐付けを検証 |
| US10 | 経路条件を調整して再算出する | 3 | 完了 | 期限・貨物種別の条件調整（再算出）、候補なし時の条件協議依頼。経由地追加は IT5 以降 |
| US11 | 経路情報を予約に紐付ける | 3 | 完了 | `CargoItinerary`（Leg 群）を予約へ紐付け、`ROUTE_PROPOSED` 遷移 |
| US12 | 確定経路を荷主に通知する | 2 | 完了（一部次 IT） | 通知記録を登録。通知先の荷主是正・通知内容確認画面は IT5 引き継ぎ |
| US13 | 予約を確定する | 3 | 完了 | `CONFIRMED` / 差戻し `ROUTING_IN_PROGRESS` / `CANCELLED`（キャンセル通知） |
| US14 | 追跡番号を発行する | 2 | 完了 | 一意採番・`TRACKING_ISSUED` 遷移・荷主通知記録。採番主体の Tracking 移行は IT5-6 |

## 技術的成果

- **Domain**: `CargoItinerary` / `Leg`（連結制約・`expectedArrivalTime`）、`Cargo` の状態遷移（紐付け・確定・差戻し・追跡番号発行・キャンセル）とドメイン不変条件（旅程の端点一致・到着期限内、日付単位比較）。
- **Application**: `RouteCargoService`（US09/US11）、`ConfirmBookingService`（US13）、`AssignTrackingNumberService`（US14）、`NotificationPort`、`CargoRoutedEvent`、`FindRouteCandidatesService`（Try T2）。
- **Infrastructure**: migration 004（`leg`・`cargo.tracking_number` nullable+部分UNIQUE・`notification_record`）、旅程の leg 入替トランザクション永続化、Booking 側経路候補 ACL（`KyselyRouteCandidateReader`）、通知記録アダプタ、外部経路 `AbortSignal.timeout`（Try T3）。
- **Presentation / UI**: `/bookings/{id}/route` 経路割り当て画面（候補選択・条件調整・条件協議依頼）、予約詳細のロール別アクション（通知・確定・差戻し・キャンセル・追跡番号発行）、航海更新の確認前 日付時系列検証（Try T6）。
- **設計同期**: ADR-008（候補 Port 境界・追跡番号暫定採番・共有 DB 直読の盲点）を起票、domain-model / data-model / ui_design を IT4 実績へ同期。

## 品質指標

| メトリクス | 実績 | 目標 | 判定 |
| :--- | :--- | :--- | :--- |
| `npm run verify` | 45 files / 297 tests green | 全 green | PASS |
| lint / typecheck / arch | no violation | 全 green | PASS |
| カバレッジ（全体 statements） | 94.29% | 75% | PASS |
| カバレッジ（全体 branches） | 83.68% | — | PASS |
| CI（Lint/Typecheck/Arch/Test） | success（run 30439517437） | success | PASS |
| CI（E2E Playwright） | success | success | PASS |
| SonarQube Quality Gate | 未実行（ローカル未設定。CI 自動チェックで代替） | PASS | 保留 |

> SonarQube はローカル環境未設定（未起動・トークン未設定）のため品質ゲートをローカルで実行できなかった。CI の lint/typecheck/arch/test/E2E がすべて success であることで静的品質を担保した。IT5 opening で `operating-qt` によるローカル SonarQube 整備を Try（T5）とした。

## レビュー結果

XP 5 視点のマルチパースペクティブレビューを実施（[レビューレポート](../review/IT4実装_review_20260729.md)）。

主なクローズ内対応（8 件）:

- `Cargo.assignRoute` にドメイン不変条件（旅程の端点一致・到着期限内）を追加し、不正な経路確定を集約で拒否。
- 確定 POST はクライアント提供値を信頼せず、永続化済みの予約条件のみで候補を再解決（多層防御）。
- 越権遮断（経路設計者の営業専用操作・営業の追跡番号発行）を E2E で検証。期限当日着の境界値、US14 通知記録を追加。
- 候補選択ラベルの語彙衝突（確定→割り当てる）を解消。`voyage.controller` の重複述語を統一。
- ui_design の正典ドリフト（ラジオ/htmx 未実装・追跡表示ボタン・キャンセル条件・文言）を実装実態へ修正。

## 課題と残作業（IT5 引き継ぎ）

- **US12 通知先の荷主是正と通知内容確認画面**（荷主メール取得 ACL・経由港/所要日数/到着予定日/料金概算の確認画面）。
- **通知・イベントの原子性方針**（コミット後副作用を冪等リスナー/アウトボックスで結果整合、ADR-005 整合）。Tracking 購読着手前に確立。
- **`leg.load_time` / `unload_time` の NOT NULL 化** migration とフォールバック削除。
- **共有 DB 直読の統制盲点**（Booking→Routing テーブル）へのスキーマ契約テスト、または Routing 読み取り ACL API 化の検討。
- **ローカル SonarQube 整備**（クローズの品質ゲートを CI 頼みにしない）。
- **追跡番号採番主体の Tracking Context 移行**と US14 の貨物状態「受領待ち（NOT_RECEIVED）」表現（Tracking 実装時）。

詳細は [イテレーション 4 ふりかえり](retrospective-4.md) を参照。
