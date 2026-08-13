-- 請求書に種別を持たせる（US30。ADR-020）。
--
-- V1 は `booking_id UUID NOT NULL UNIQUE` に「UNIQUE 制約で二重請求を防止する」と
-- 書いた。**その判断は正しかったが、想定していた請求書は 1 種類だけだった。**
--
-- US30 でキャンセル料の請求書が生まれる。輸送料金の請求書が既にある予約には
-- **2 枚目が入らない** — 輸送中の貨物をキャンセルした荷主に、キャンセル料を
-- 請求する手段がシステムに無い。
--
-- **二重請求の防止は捨てない。** 防ぐ対象を「予約ごとに 1 枚」から
-- 「予約と種別の組ごとに 1 枚」へ狭める。輸送料金の請求書は依然として
-- 予約に 1 枚しか作れない。
--
-- **既存の行はすべて輸送料金である**（キャンセル料はまだ存在しない）。
-- 既定値で埋めることで、列が無かったころの行も読める（V22 / V26 / V32 と同じ判断）。
ALTER TABLE invoice
    ADD COLUMN invoice_type VARCHAR(20) NOT NULL DEFAULT 'TRANSPORT';

ALTER TABLE invoice
    ADD CONSTRAINT chk_invoice_type
        CHECK (invoice_type IN ('TRANSPORT', 'CANCELLATION'));

-- **口約束にしない**（ADR-018 で咎めた非対称を繰り返さない）。
-- 種別ごとの二重請求は、ここが防ぐ。
ALTER TABLE invoice
    ADD CONSTRAINT uq_invoice_booking_type UNIQUE (booking_id, invoice_type);
