# 0010 追跡番号の採番ポリシーと Tracking Context の集約境界

US14（追跡番号発行）の実装に際し、`TrackingNumber` の採番方式と Tracking Context の集約境界を確定する。

日付: 2026-06-22

## ステータス

2026-06-22 提案されました（IT5 タスク 1.1）

## コンテキスト

US14 は経路設計者が「予約確定」状態（`BookingStatus.Confirmed`）の予約に対して追跡番号を採番し、荷主に通知するユースケース。data-model.md では `tracking_activity.tracking_number` のスキーマが `VARCHAR(20)` と定義されており、この制約と整合する採番方式が必要。

検討対象：

1. **UUID v4**: 衝突しないが 36 文字（`8-4-4-4-12` 形式）で `VARCHAR(20)` をオーバーする
2. **UUID v4（ハイフン除去 + 短縮）**: 16 進 32 文字または BASE64 22 文字。20 文字以下にするには切り詰めが必要で衝突確率が上昇
3. **プレフィクス + シーケンス（例: `TN-000001`）**: 業務的に読み取り可能、`VARCHAR(20)` に余裕、DB 採番に直接対応
4. **プレフィクス + タイムスタンプ + 連番（例: `TN-20260622-001`）**: 業務的に読み取り可能、月別分割が容易、最大 17 文字で `VARCHAR(20)` に収まる

加えて Tracking Context の集約境界も決める必要がある：

- `TrackingActivity`（追跡レコード集約ルート）
- `TrackingActivityEvent`（追跡イベント、集約内エンティティ）
- `TrackingExceptionEvent`（追跡例外、集約内エンティティ。IT7 で実装）

Booking Context との連携は ACL（`CargoSnapshot`）+ ドメインイベント（`TrackingNumberAssigned` / `HandlingActivityRegisteredEvent`）で行い、相互参照しない方針（domain-model.md L200-234）。

## 決定

### (a) 採番方式: プレフィクス `TN-` + 6 桁シーケンス（例: `TN-000001`）を採用する

- 形式: `TN-` + 6 桁ゼロ埋め整数（合計 9 文字、`VARCHAR(20)` に十分余裕）
- 採番元: `tracking_activity.id`（`BIGSERIAL`）をリポジトリで取得した上で文字列化する、または別シーケンス
- IT5 では `BIGSERIAL` 値をそのまま 6 桁に整形する単純実装で開始する。6 桁を超えた時点で 7 桁以上に拡張する仕様（最大 9 桁 = 999,999,999 まで `VARCHAR(20)` に収まる）
- 一意性は `tracking_activity.tracking_number UK` 制約で保証

### (b) 不採用案

| 案 | 不採用理由 |
| :--- | :--- |
| UUID v4 | 36 文字で `VARCHAR(20)` 制約に違反 |
| UUID v4 ハイフン除去 / BASE64 短縮 | 切り詰めによる衝突確率上昇と業務不可読 |
| `TN-YYYYMMDD-001` 日付ベース | 月別シャーディングが現行スコープで不要、9 桁案でシンプル優先 |

### (c) Tracking Context の集約境界

- `TrackingActivity`（集約ルート）が `TrackingNumber` / `TrackingBookingId` / `List[TrackingActivityEvent]` / `List[TrackingExceptionEvent]` を保持
- `TrackingNumber` は `opaque type String`（domain-model.md L697 / L748）。採番後の変更不可
- `TrackingBookingId` は `opaque type String`。Booking Context の `BookingId` とは別型（コンテキスト分離）
- `currentStatus()` はイベント履歴から導出（永続化しない）。書込時に Read Model（`tracking_activity.transport_status`）にキャッシュして O(1) 読取
- IT5 で実装するのは `TrackingActivity` + `TrackingNumber` + `TrackingBookingId` の最小骨格。`TrackingActivityEvent` は US15（IT5）で追加、`TrackingExceptionEvent` は IT7 で追加

### (d) 採番冪等性

- 同一 `bookingId` に対する `AssignTrackingNumberCommand` は冪等（再発行禁止）
- `Cargo.trackingNumber.isDefined` で判定し、既存ありの場合は既存の `TrackingNumber` を返却（`AlreadyAssigned` エラーは投げず冪等成功扱い）

## 帰結

### 良い点

- 追跡番号が `TN-000001` 形式で業務担当者に読み取りやすい
- `BIGSERIAL` を利用するため採番ロジックがシンプル
- `VARCHAR(20)` 制約に余裕（9 文字 / 20 文字）があるため、将来拡張が容易
- 集約境界が明確で、Booking と Tracking の独立性が担保される

### 悪い点

- `BIGSERIAL` をユーザーに露出するため ID 推測攻撃に弱い（公開照会 URL `/public/tracking/:trackingNumber` で連番がそのまま見える）。重要業務データ漏洩リスクが高い場合はランダム化を再検討（IT6 以降の課題）
- IT5 では `tracking_activity.id` を文字列化する単純実装のため、リトライ時の番号スキップが発生しうる（リポジトリ実装で吸収）

### 後続タスクへの影響

- IT5 タスク 1.2 / 1.3: 本 ADR の採番方式に従って `TrackingNumber` 値オブジェクトとリポジトリを実装する
- IT5 タスク 2.x: `TrackingActivity.addEvent` で `TrackingActivityEvent` を追加する（US15）
- IT7: `TrackingExceptionEvent` 追加時に本 ADR の集約境界判断を踏襲する
- セキュリティ強化（ID 推測防止）が必要になった時点で UUID v4 + 別表チェック / ハッシュ化を再検討する別 ADR を起案する
