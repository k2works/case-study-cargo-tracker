-- 予約のもとになった見積（US01・US23・注 N12）。
--
-- **貨物スナップショットには混ぜない。** あちらは追跡番号が主キーで、
-- 追跡番号は輸送が始まってから決まる。見積が結び付くのは予約の時点なので、
-- 混ぜると「まだ行が無い」ところへ概算を書くことになる。
--
-- **見積を経ない予約では行が無い。** 「概算が無い」は欠損ではなく普通の状態で、
-- 0 円で埋めると 0 円の見積があったと読まれる（S61 は概算行を出さない）。
CREATE TABLE booking_quotation (
    booking_id     VARCHAR(36)   PRIMARY KEY,
    quotation_id   VARCHAR(36)   NOT NULL,
    -- 見積時の概算。**請求との差を経理が見る**（S61）。
    quoted_amount  NUMERIC(14,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    quoted_at      TIMESTAMPTZ   NOT NULL,
    projected_at   TIMESTAMPTZ   NOT NULL
);
