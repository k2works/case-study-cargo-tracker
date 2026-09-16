-- 要確認一覧（S70）からシミュレーション由来を外す（US33 §受入基準 3 / [ADR-0020] 決定 4）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（booking_read_db）
--
-- **要確認は荷主を持たない。** 対象（target_type / target_id）は予約か荷主なので、
-- 登録のときに解決して列に書く。あとから結合しようとすると、対象の種類ごとに
-- 別の表を引くことになり、読むたびに条件が増える。
--
-- **既定は FALSE。** 列が無かったころの要確認はすべて本物である。
ALTER TABLE attention_item
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN attention_item.simulated IS
    'シミュレーション由来か。要確認一覧（S70）は既定で外す。登録時に対象から解決する（ADR-0020 決定 4）';

CREATE INDEX ix_attention_item_simulated ON attention_item (simulated);
