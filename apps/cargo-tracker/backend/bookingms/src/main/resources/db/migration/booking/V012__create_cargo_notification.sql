-- 荷主への通知履歴（US12 §受入基準 4）。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- **主キーに通知日時を含める。** 採番するとリプレイのたびに行が積み上がる
-- （cargo_revision と同じ形。ADR-0008）。同じイベントを 2 度読んでも同じ行になる。
--
-- 送信基盤はスコープ外で、通知は現行の手作業（電話・メール）で行う。ここに残るのは
-- 「いつ・誰に・何を伝えたか」で、荷主から「聞いていない」と言われたときに
-- 突き合わせる材料になる。
CREATE TABLE cargo_notification (
    booking_id      VARCHAR(36)  NOT NULL,
    notified_at     TIMESTAMPTZ  NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    summary         VARCHAR(500) NOT NULL,
    notified_by     VARCHAR(50)  NOT NULL,
    PRIMARY KEY (booking_id, notified_at)
);

-- 予約詳細（S22）は新しい通知から順に読む。
CREATE INDEX idx_cargo_notification_booking
    ON cargo_notification (booking_id, notified_at DESC);

-- 経路設計へ戻した理由（US12）。営業が戻した理由を経路設計者が読む。
-- **引き渡し（routing_requested_at）と別の列にする。** 同じ列に書くと、
-- 「引き渡した」と「通知後に戻した」が区別できなくなる。
ALTER TABLE cargo_summary ADD COLUMN returned_to_routing_at TIMESTAMPTZ;
ALTER TABLE cargo_summary ADD COLUMN return_reason VARCHAR(200);
