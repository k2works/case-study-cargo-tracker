# 0013 Notification updateNotification 主キー設計

`notification` テーブルの更新経路 (`updateNotification`) が使う識別戦略を
`(booking_id + created_at)` 複合キーから明示的な `notification_id` (UUID)
サロゲートキーへ移行する

日付: 2026-07-03

## ステータス

提案 (2026-07-03、IT7 T6-08 で起票)

IT6 の PostgresNotificationRepository (`ac69b961` / `4eb19eea`) 実装で
`updateNotification` が `WHERE booking_id = ? AND created_at = ?` の複合キーで
単一レコードを特定していることを iteration_report-6.md の Problem 節と
retrospective-6.md の Try (T6-08) で指摘した。本 ADR は移行方針を確定する。

## コンテキスト

現行の `notification` テーブル (IT6 追加、`20260914100200_create_notification.sql`)
は以下のスキーマ:

```sql
CREATE TABLE notification (
    id           BIGSERIAL PRIMARY KEY,      -- サロゲート (自動採番)
    booking_id   VARCHAR(20) NOT NULL,
    channel      VARCHAR(20) NOT NULL,
    subject      VARCHAR(200) NOT NULL,
    body         TEXT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'Pending',
    created_at   TIMESTAMPTZ NOT NULL,
    sent_at      TIMESTAMPTZ,
    failure_reason TEXT,
    version      INTEGER NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

現状の実装 (`PostgresNotificationRepository.updateImpl`):

```sql
UPDATE notification
SET status = ?, sent_at = ?, failure_reason = ?, version = version + 1
WHERE booking_id = ? AND created_at = ?
```

問題点:

1. **理論上の衝突リスク**: 同一 `booking_id` × 同一 `created_at` (マイクロ秒
   単位) の複数レコードが原理的に存在し得る。テストや seed 投入時に
   `created_at = NOW()` を明示指定した場合、精度によっては衝突する
2. **業務キー変更耐性の欠如**: `created_at` は監査カラムであり、業務上の
   識別子ではない。将来の要件変更で「通知を修正して再送信」等が発生した場合、
   `created_at` を更新すると識別子が失われる
3. **Domain との齟齬**: `Cargotracker.Notification.Domain.Model.Notification`
   は `id :: NotificationId` (Text) を持つ想定だが、Infrastructure が
   複合キーを使うため Application 層でも識別子の一貫性が保てない

## 決定

**Option A (採用): `notification_id UUID UNIQUE` カラムを追加し、単一のサロゲート
Text 識別子として使う。**

Domain の `NotificationId` (Text UUID) をそのまま DB に永続化し、
`updateNotification` は `WHERE notification_id = ?` で更新する。
`id` (BIGSERIAL) は内部の物理識別子として維持 (data-model.md §1 サロゲート +
業務キー規約に準拠)。

不採用:

- Option B: 現行の `(booking_id, created_at)` に `UNIQUE` 制約を追加する
  - 制約変更のみで済むが、`created_at` の精度依存問題と業務キー変更耐性
    問題が残る
- Option C: `(booking_id, channel, subject)` を業務複合キーとする
  - subject 変更で識別が壊れるため不採用

## 実装計画

### Phase 1: Migration (次イテレーション)

```sql
-- 20260XXXXX_add_notification_id.sql
-- migrate:up
ALTER TABLE notification
  ADD COLUMN notification_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX idx_notification_notification_id
  ON notification (notification_id);

-- 既存データがある場合は uuid_generate_v4() で自動採番されるため
-- 追加のデータ移行は不要

-- migrate:down
ALTER TABLE notification DROP COLUMN notification_id;
```

### Phase 2: Domain / Application

- `Cargotracker.Notification.Domain.Model.Notification` に
  `nId :: NotificationId (newtype Text)` を追加
- `SendClaimNotificationCommand` で UUID v4 を Application 層で生成し、
  `mkNotification` に渡す
- 既存の `updateNotification` シグネチャを `(NotificationId -> Notification -> IO ...)`
  に変更

### Phase 3: Infrastructure

- `PostgresNotificationRepository.updateImpl` を
  `WHERE notification_id = ?` に書き換え
- `saveImpl` で `notification_id` を明示的に INSERT に含める

## 結果

- **良**:
  - 業務キー変更耐性 (subject 変更 / 再送信 / 修正) を確保
  - Application 層で採番した Text UUID を Domain / Infrastructure で一貫使用
  - 他 BC (Handling / Tracking) からの Cross-BC 参照を `notificationId :: Text`
    で行える (ADR-0004 Rule 4 準拠)
- **悪**:
  - Migration が 1 本増える
  - 既存の in-memory Repository / テストヘルパを notification_id 対応に更新
    する必要あり (影響範囲は SendClaimNotificationCommandSpec と
    PostgresNotificationRepository 周辺)
- **補**: `id` (BIGSERIAL) は物理識別子として維持し、SQL ジョインや page
  cursor 等は引き続き `id` を使う。Domain / Application からは
  `notification_id` のみを見せる

## 影響範囲

- data-model.md §notification: スキーマ追記 (Phase 1 で反映)
- Cargotracker.Notification.Domain.Model.Notification: `nId` フィールド追加
- Cargotracker.Notification.Application.SendClaimNotificationCommand:
  UUID 採番の追加
- Cargotracker.Notification.Infrastructure.PostgresNotificationRepository:
  update 経路の書き換え

## 参照

- iteration_report-6.md Problem: NotificationRepository の updateNotification
  が booking_id + created_at を複合キーに使用
- retrospective-6.md Try T6-08: ADR-0013 起票 (Notification 主キー設計)
- ADR-0012 (Tx 境界と Cross-BC 参照ポリシー) — 本 ADR は ADR-0012 の
  Text-DTO 原則に整合
- ADR-0004 (Cross-BC 参照に ShipperRef VO 導入) — 本 ADR で
  `notificationId :: Text` を Cross-BC 参照キーとして採用する根拠
