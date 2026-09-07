-- 荷主向けの追跡（US18 / IT8）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_read_db ER 図」
--
-- IT7 では作れなかった。TrackingInitializedEvent に荷主 ID が無く、trackingms は
-- それを得る手段を持たなかったため（載せる相手のいない NOT NULL は作れない）。
-- IT8 で 3 本すべて（TrackingNumberIssuedEvent → InitializeTrackingCommand →
-- TrackingInitializedEvent）に shipperId を足したので、ここで受ける。
--
-- **NULL を許す。** 正典は NOT NULL だが、IT7 に作られた行には荷主 ID が無い。
-- 既存の行を読めなくする不変条件は足さない（復元では検査せず、新規の受け入れ時
-- だけ検査する）。開発データを作り直せば全行に入る。

ALTER TABLE tracking_summary ADD COLUMN shipper_id VARCHAR(36);

-- 荷主向け一覧（US18）は自社の貨物だけを出す。絞り込みの索引。
CREATE INDEX idx_tracking_summary_shipper ON tracking_summary (shipper_id);
