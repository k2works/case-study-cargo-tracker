-- 荷主が「どこまで知らせを読んだか」（US39）。
--
-- お知らせ（tracking_notice）は IT8 から記録している。本表は、その記録のうち
-- **まだ見ていないものだけ**をポップアップで出すために、利用者ごとの位置を覚える。
--
-- **既読をブラウザに持たない。** 荷主は自宅の PC と現場の端末を使い分けるため、
-- 端末に持つと同じ知らせが行く先々でもう一度出る。
CREATE TABLE shipper_notice_ack (
    -- 認証の主体（authms の利用者 ID）。荷主 ID ではない
    -- ——同じ荷主に複数の担当者がつく場合、読んだ位置は担当者ごとに違う
    username         VARCHAR(50) PRIMARY KEY,
    -- 読み終えた tracking_notice.id。0 は「まだ何も読んでいない」
    last_notice_id   BIGINT NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_shipper_notice_ack_not_negative CHECK (last_notice_id >= 0)
);
