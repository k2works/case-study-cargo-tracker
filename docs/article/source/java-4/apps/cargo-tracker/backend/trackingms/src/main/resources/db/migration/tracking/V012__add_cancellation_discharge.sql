-- キャンセルの陸揚げ地と、追跡を閉じた印（UC22 / US30。IT15 T5・T6）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（tracking_summary）
--
-- **承認しても追跡は閉じない**（TrackingActivity 不変条件 9）。貨物はまだ船の上に
-- あり、陸揚げの荷役を記録できなければならない。閉じるのは **その港の荷降しを
-- 受けてから** なので、列も 2 つに分かれる：どこで降ろすか（承認のとき）と、
-- 閉じたか（荷降しのあと）。
--
-- **既定は「閉じていない」。** 列が無かったころの追跡はすべて動いている追跡なので、
-- 既定値が業務上正しい（不変条件の追加で既存行を読めなくしない）。
ALTER TABLE tracking_summary
    ADD COLUMN cancellation_discharge_unlocode VARCHAR(5),
    ADD COLUMN closed BOOLEAN NOT NULL DEFAULT FALSE;
