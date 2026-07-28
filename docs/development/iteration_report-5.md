---
title: イテレーション 5 完了報告書
description: IT5（追跡番号発行 US14・荷役記録 US15・引取 US16・貨物状態手動更新 US17・Tracking/Handling Context 確立・3 BC イベントコレオグラフィ）の完了報告
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 5 完了報告書

## エグゼクティブサマリー

Phase 3（追跡・荷役・例外処理）の前半として、追跡番号発行（US14）、荷役作業の記録（US15）、引取作業の記録（US16）、貨物状態の手動更新（US17）を中盤インサイドアウトの TDD で完成させました。Tracking Context（`TrackingActivity` 集約）と Handling Context（`HandlingActivity` 集約）を新設し、IT4 で確立した通知基盤（ADR-0002・ドメインイベント）を起点に Booking・Tracking・Handling の 3 BC をイベントコレオグラフィで連携させました。計画 14 SP を 100% 消化し、RSpec 294 examples 0 failures・新規カバレッジ 92.7% を達成しました。序盤の独立コミット枠で技術的負債（T16 / T21+T24 / T25 / T26 / T22）を全消化し、マルチパースペクティブレビューの高優先 5 件をクローズ前に対応しました。IT5 では Release を出さず（Release 0.3 は Phase 3 完了＝IT6 時）、Phase 3 前半を締めました。

## 達成状況

| US | 概要 | SP | BC | 状態 |
|:---|:-----|:--|:---|:-----|
| US14 | 追跡番号を発行する | 3 | Tracking | ✅ 完了 |
| US15 | 荷役作業を記録する | 5 | Handling | ✅ 完了 |
| US16 | 引取作業を記録する | 3 | Handling | ✅ 完了 |
| US17 | 貨物状態を手動更新する | 3 | Tracking | ✅ 完了 |
| **計** | | **14** | | **100%（実績 14 SP）** |

- **局面**: 中盤（インサイドアウト・データ層→ドメイン層→アプリケーション→UI）。Phase 3 前半。

受入基準の充足状況（正直な評価）:

- **US14**: 充足。経路設計者（MVP は営業代替）の明示発行操作により `AssignTrackingNumber` が Booking 公開 API 経由で CONFIRMED→TRACKING_ISSUED を検証し、成立時のみ `TrackingActivity` を生成・一意採番、`tracking_number_issued` で荷主へ通知。通知本文の追跡 URL は次 IT（送信アダプタ実装時）に持ち越し。
- **US15**: 充足。作業種別（受領・積込・荷降し）の記録、貨物状態の自動更新、荷主への状態変更通知を実装。作業場所が予定ルートと異なる場合は MISROUTED 警告を分離表示（記録は阻止しない）。
- **US16**: 充足。CLAIM 選択時に荷受人確認フィールドを動的表示（Stimulus）し、荷受人確認を必須化。記録後 DELIVERED へ遷移し、精算処理の開始条件を満たす。
- **US17**: 充足。追跡番号入力→現在情報の詳細確認→状態・位置・日時の手動更新→追跡イベント履歴追加→状態変更種別に応じた荷主通知を実装。

## 技術的成果

- **Tracking Context 新設**: `TrackingActivity` 集約（`issue` / `reconstitute` / `apply_handling` / `update_status`）、`TrackingNumber`（`TRK-` + 8 桁 hex）、`TrackingStatus`（NOT_RECEIVED〜CLAIMED・`for_handling` マッピング）。`tracking_activities` テーブル（`tracking_number` UK・`booking_id` UK・`transport_status`・`lock_version`）と `tracking_handling_events`（追跡イベント履歴）で永続化。
- **Handling Context 新設**: `HandlingActivity` 集約（`register`・`route_check` デシジョンテーブル: LOAD/UNLOAD=旅程照合で MISROUTED・RECEIVE/CLAIM=想定港照合で warning・CLAIM は荷受人確認必須）、`HandlingType` / `RecipientConfirmation` / `CargoSnapshot`（ACL）/ `HandlingActivityHistory`（Read Model）。`handling_activities` テーブルで永続化。
- **追跡番号発行（US14）**: 経路設計者（MVP は営業代替）の明示発行操作をトリガーとし、`AssignTrackingNumber` が Booking 公開 API 経由で CONFIRMED→TRACKING_ISSUED を検証・成立時のみ `TrackingActivity` を生成、`tracking_number_issued` で荷主通知。宙吊り状態（Tracking 生成済・Booking 未遷移など）の冪等回復を備える。
- **3 BC イベントコレオグラフィ**: `handling_activity_registered` を起点に、Tracking が状態・履歴を同期、Booking が状態を同期（LOAD→IN_TRANSIT・CLAIM→DELIVERED）、荷主へ状態変更通知を送出。荷役の前提状態ガード（CLAIM は IN_TRANSIT 必須など）により Booking/Tracking の乖離を防止。
- **状態手動更新（US17）**: `UpdateTrackingStatusManually` ユースケース・追跡イベント履歴・状態変更通知を実装。
- **BC 独立性**: Tracking/Handling→Booking の一方向依存に限定。連携は `CargoSnapshot` ACL 射影とプリミティブ Hash ペイロードのドメインイベントのみ。Packwerk privacy 違反ゼロ。
- **UI**: 予約詳細の追跡番号発行ボタン、`handling_events`（登録・履歴・CLAIM 動的表示 Stimulus・MISROUTED 警告分離）、`trackings`（追跡番号入力→詳細→手動更新）、ダッシュボードの handler/tracker/sales 導線、enum 日本語ラベル。

### 技術的負債の返済（序盤の独立コミット枠で全消化）

「余力次第にしない」という IT4 ふりかえりの反省を踏まえ、追跡・荷役の本体着手より前に序盤の独立コミット枠で以下を全消化しました。

- **T16**: CI 相当のローカル検証をクローズ前チェック手順として定着。
- **T21+T24**: `voyages` の楽観ロック追加と `reconstitute` による生成／復元責務の分離。
- **T25**: `legs` の全置換を旅程変更時のみ実行するよう条件化。
- **T26**: 通知購読 `install_once` に冪等ガードを追加（多重購読・多重発行を防止）。
- **T22**: US08 多区間フォールバック（寄港地接続評価）・US25 差分確認画面を実装し受入基準を完全充足。

## 品質指標

| 指標 | 結果 |
|:-----|:-----|
| RSpec | 294 examples, 0 failures |
| カバレッジ | 新規 92.7%（DoD: ドメイン 85%・全体 80% を超過。全体は高水準） |
| RuboCop | no offenses（ActiveRecord 禁止 cop 含む） |
| Packwerk | validate/check green（enforce_dependencies + privacy・新 pack tracking/handling） |
| Brakeman | Security Warnings 0 |
| bundler-audit | 0 vulnerabilities |
| CI（Backend CI） | success |
| SonarQube | Quality Gate PASS（新規カバレッジ 92.7%・重複 0.0%・違反 0・Bug 0・Vulnerability 0） |

## レビュー結果

5 視点のマルチパースペクティブレビューを実施（[レポート](../review/IT5実装_review_20260728.md)）。

- **クローズ前対応（高 5 件）**: すべてクローズ前に対応済み。
  - H1: MISROUTED 警告の分離表示
  - H2: CLAIM 選択時の荷受人確認フィールド動的表示（Stimulus）
  - H3: 荷役の前提状態ガード（Booking/Tracking 乖離防止）
  - H4: 3 ハンドラ結合 spec
  - H5: ui_design（未実装ルート）整合
- **中優先（7 件）**: 5 件対応（M1 日時差分正規化・M2 発行冪等回復・M3 enum 日本語化・M4 ADR-0002 追記・M5 route_check 補完）。M6（荷役二重登録防止）・M7（ロック競合テスト）は次 IT へ繰越。
- **設計反映**: iteration_plan-5 の「設計への反映が必要」8 点を domain-model / data-model / ui_design / architecture_backend / ADR-0002 へ反映。
- **プレースホルダ整合性確認**: US17 の UI 到達導線欠落（`trackings#new` プレースホルダ）を発見・修正。

## 課題と残作業

- **M6 荷役二重登録防止（T28）**: 未対応。次 IT。
- **M7 ロック競合テスト（T29）**: 未対応。次 IT。
- **状態機械 precondition の設計 DoD 化（T30）**: 次 IT。
- **MISROUTED→routing_status 反映（T32）**: 次 IT。
- **`handling_activity_registered` ファンアウトの非トランザクション性**: 将来 Outbox パターンで対応。現状は ADR-0002 の既定で受容。
- **追跡番号 distinct テスト**・**route_check の leg 単位照合**: 次 IT でテスト・照合粒度を強化。
- **US14 通知本文の追跡 URL**・**handler 追跡導線**: 送信アダプタ実装時に対応（次 IT）。

## 次イテレーション（IT6）引き継ぎ

- 持ち越しストーリーなし（IT5 スコープ完了）。IT6 は Phase 3 後半（例外処理）に進む。
- レビュー繰越の M6（荷役二重登録防止 T28）・M7（ロック競合テスト T29）・状態機械 precondition の DoD 化（T30）・MISROUTED→routing_status 反映（T32）を IT6 計画に組み込む。
- `handling_activity_registered` ファンアウトの非トランザクション性は将来 Outbox 化を検討（ADR-0002 の既定で当面受容）。
- 追跡番号 distinct テスト・route_check の leg 単位照合を追加し、Tracking/Handling の堅牢性を高める。
- US14 通知本文の追跡 URL・handler 追跡導線は送信アダプタ実装時に完成させる。
- Release 0.3 は Phase 3 完了（IT6）時に発行する。

## 関連ドキュメント

- [イテレーション 5 計画](iteration_plan-5.md)
- [イテレーション 5 ふりかえり](retrospective-5.md)
- [IT5 実装レビュー](../review/IT5実装_review_20260728.md)
- [ドメインモデル](../design/domain-model.md)（Tracking / Handling Context）
- [データモデル](../design/data-model.md)（tracking_activities / tracking_handling_events / handling_activities）
- [ADR-0002 ドメインイベントによる通知基盤](../adr/0002-domain-events-and-notification.md)
- [ADR-0003 越境識別子・ACL](../adr/0003-cross-context-identifier-and-acl.md)
- [リリース計画](release_plan.md)
- [CHANGELOG](../../CHANGELOG.md)

## 更新履歴

| 日付 | 版 | 変更内容 | 担当 |
|:-----|:---|:---------|:-----|
| 2026-07-28 | 初版 | IT5 完了報告書を作成 | 開発チーム |
