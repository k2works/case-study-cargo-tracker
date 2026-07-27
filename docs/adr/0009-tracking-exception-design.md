# ADR 0009: 追跡例外を Tracking 集約内エンティティで管理し、通知はベストエフォート・エスカレーションは登録時 1 回評価とする

IT7（US17 貨物状態手動更新・US19 遅延例外・US20 破損/紛失例外）で、Tracking Context の例外処理をどのモデル・整合性境界で実装するかを定める。

日付: 2026-07-27

## ステータス

2026-07-27（IT7）承認（暫定）。実装で完遂。エスカレーション再評価・TrackingExceptionDetectedEvent 配信・管理職ワークリストは後続イテレーションの課題として明示する。

## コンテキスト

IT6 で `tracking_exception_event` テーブルと sqlcgen 構造体は「枠のみ」生成済み。IT7 で例外処理（登録・EXCEPTION 遷移・エスカレーション・対応報告・解決）と貨物状態手動更新を作り込む。開発レビュー（XP 5 視点）で以下が論点となった。

- 例外を集約内エンティティとするか独立集約とするか。
- 例外発生時の貨物状態（EXCEPTION）をどう表現し、解決時にどう復帰するか。
- 通知（荷主・管理職エスカレーション）の整合性境界。
- DELAY の 48 時間エスカレーションを登録時に評価するか、経過後も再評価するか。
- Tracking → Booking/Notification のイベント配信（`TrackingExceptionDetectedEvent`）。

## 決定

1. **例外は Tracking 集約（`TrackingActivity`）内のエンティティ（`TrackingExceptionEvent`）として管理する**。`AddException`/`ResolveException`/`HasActiveException` は集約ルートメソッド経由でのみ操作する（domain-model の集約設計に準拠）。

2. **貨物状態 EXCEPTION は別カラムで二重持ちせず、`CurrentStatus()` が `HasActiveException()` を先頭判定して算出する**。未解決例外がある間は EXCEPTION、解決すると追跡イベント履歴（最新の非 UNKNOWN 状態）へ自然復帰する。状態を独立カラムで持たないことで二重管理の不整合を防ぐ。手動状態更新（US17）は EXCEPTION の直接指定を拒否し、EXCEPTION は例外エンティティ経由でのみ設定する。

3. **通知はベストエフォートとする**。集約の永続化（`repo.Save`）コミット後に荷主・管理職通知を送信し、通知失敗はログに留めてユースケースは成功扱いとする。永続化済みの結果に対し通知失敗で 422 を返すと、利用者の再送で例外二重登録を招くため。実メール導入時は Outbox パターンを検討する（ADR-0008 と同方針）。

4. **エスカレーション判定は `EscalationPolicy`（ステートレスドメインサービス）で例外登録時に 1 回評価し `escalationFlag` を固定する**。LOST は即時 `true`、DELAY は `occurredAt` から 48 時間超過（`>` 判定）で `true`。判定基準時刻は `ExceptionService` に注入した `Clock` から渡す（テストで固定時刻に差し替え可能）。**登録後の再評価は行わない**（既知の制約）。

5. **BC 独立性**: `NotificationPort` は Tracking の application に定義し、実体（ログ実装）を合成ルートで注入する。`TrackingExceptionDetectedEvent`（Tracking→Booking/Notification）は本 IT では配信せず、結合を作らない。

## 影響

- IT7 実装: 例外ドメイン・`ExceptionService`（登録/解決/手動更新・ベストエフォート通知）・migration 000015（resolution_notes/location_unlocode）・例外 sqlc・リポジトリ例外永続化（id ベース INSERT/UPDATE）・`ExceptionHandler`（ROLE_TRACKER + 破損は ROLE_HANDLER）。
- 二重解決は `ErrExceptionAlreadyResolved` で拒否する。
- 後続イテレーション（IT8）の課題として明示: (a) DELAY エスカレーションの登録後再評価（定期バッチ）、(b) `TrackingExceptionDetectedEvent` の配信と Booking/Notification 連携、(c) 管理職向け緊急例外ワークリスト、(d) 新到着予定日（ETA）の構造化、(e) 紛失解決の終端（CLOSED）扱い、(f) 例外の位置 index から安定 ID アドレッシングへの移行。

## 参考

- [ADR-0003](0003-transport-status-canon.md) TransportStatus / RoutingStatus 正典
- [ADR-0008](0008-bc-sync-consistency-boundary.md) BC 間同期の整合性境界
- [IT7 計画](../development/iteration_plan-7.md)
- [IT7 開発レビュー](../review/it7_go_review_20260727.md)
