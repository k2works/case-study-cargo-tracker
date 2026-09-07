-- 一覧の N+1 を解消する（IT8 レビュー 中 A / IT9 引き継ぎ H.2）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_read_db」（current_unlocode）
--
-- **一覧が JOIN しないための非正規化。** data-model.md はこの方針を採っており
-- （shipper_name・last_handling_*）、ここだけが反対側に落ちていた。
-- 1 行ごとに旅程と履歴を引いていたので、既定 50 件で 100 回超の往復になり、
-- それが 30 秒ごとに繰り返されていた。
--
-- current_unlocode は data-model.md に定義済みの列。estimated_arrival は
-- **予定の旅程の最終区間の荷降し**で、tracking_leg から導ける値だが、
-- 一覧のたびに引くと同じ往復が残るため写す。導出は投影の 1 か所（IT7 引き継ぎ 7）。

ALTER TABLE tracking_summary ADD COLUMN current_unlocode VARCHAR(5);
ALTER TABLE tracking_summary ADD COLUMN estimated_arrival TIMESTAMPTZ;

-- 既存の行にも埋める。**列を足しただけでは一覧が空欄になる**——
-- 追跡が始まったあとに列を足したので、書き手（投影）は次の更新まで動かない。
UPDATE tracking_summary s
SET estimated_arrival = (
    SELECT MAX(l.unload_time) FROM tracking_leg l WHERE l.tracking_number = s.tracking_number);

UPDATE tracking_summary s
SET current_unlocode = (
    SELECT e.location FROM tracking_event e
    WHERE e.tracking_number = s.tracking_number AND e.location IS NOT NULL
    ORDER BY e.occurred_at DESC, e.event_id DESC LIMIT 1);

-- 一覧は到着予定が近い順に並ぶ（ui_design.md「一覧の既定条件」）。
CREATE INDEX idx_tracking_summary_arrival ON tracking_summary (estimated_arrival);
