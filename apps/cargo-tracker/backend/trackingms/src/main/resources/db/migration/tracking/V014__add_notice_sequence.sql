-- 荷主への知らせ（US37 §受入基準 1・3）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（tracking_read_db）
--
-- **「新着」を決める順序がこの表に無かった**（注 N3。着手前に実測した）。
-- 主キーは event_id（UUID）で、索引も (tracking_number, occurred_at) だけ。
-- 時刻で既読を持つと、同じ時刻に複数の知らせが入ったときに取りこぼす。
--
-- **連番を足す。** 追記は `ON CONFLICT (event_id) DO NOTHING` なので、
-- リプレイでも既存行の連番は変わらない（実測）——**採番するが増えない**。
ALTER TABLE tracking_event
    ADD COLUMN sequence_no BIGSERIAL;

COMMENT ON COLUMN tracking_event.sequence_no IS
    '知らせの順序（US37）。既読位置はこの値で持つ。時刻では同時刻の取りこぼしが出る';

-- 荷主の未読は「自分の貨物の、この位置より後」で引く。
CREATE INDEX idx_tracking_event_sequence ON tracking_event (sequence_no);

-- 既読位置（US37 §受入基準 3）。
--
-- **サーバが持つ。** ブラウザに持つと、荷主が端末を使い分けたとき同じ知らせが
-- 行く先々でもう一度出る。荷主ごとに 1 行で、不変条件が無いので集約にしない。
CREATE TABLE notice_read_position (
    shipper_id         VARCHAR(36)  PRIMARY KEY,
    last_read_sequence BIGINT       NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

COMMENT ON TABLE notice_read_position IS
    '荷主ごとの既読位置（US37 §3）。サーバが持つ——ブラウザに持つと端末ごとに同じ知らせが出る';
