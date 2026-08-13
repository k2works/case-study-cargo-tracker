-- 引取が済んだ日時（IT13 レビュー C1）。
--
-- 経理の月次は「前月に引取が済んだ分」を締める作業である。請求対象一覧に日付が
-- 無いと、並んでいる貨物が前月分か当月分か判別できず、締め日をまたいだ引取が
-- 混ざったまま確定すると当月の売上計上が狂う。
--
-- **NULL を許す。** 列が無かったころに引取が済んだ予約は値を持たない。
-- NOT NULL にすると既存行が読めなくなる。
ALTER TABLE cargo ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN cargo.claimed_at IS '引取が済んだ日時。旧い行は NULL';
