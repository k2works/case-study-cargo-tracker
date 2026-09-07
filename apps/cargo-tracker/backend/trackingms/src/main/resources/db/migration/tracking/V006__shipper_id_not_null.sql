-- 荷主 ID の紐付いていない行を無くす（IT8 レビュー 中 D / IT9 引き継ぎ H.3）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_read_db」（shipper_id は NOT NULL）
--
-- **NULL の行は誰にも紐づかない。** 追跡管理者には見えるが、本来の荷主には
-- 見えないまま一覧に出続ける。件数を見る手段も無く、いつ締めるかの記録も
-- 無かった（IT8 のレビューで指摘）。
--
-- V004 で NULL を許したのは、IT7 に作られた行を読めなくしないため（新しい
-- 不変条件で既存行を壊さない）。**その行は開発データで、作り直せる。**
-- IT8 以降に作られた行はすべて荷主 ID を持つ（契約 3 本を通す）。

-- 残っている NULL 行を消す。**投影なので消せる**——真実は Event Store にあり、
-- 読み直せば作り直される（作り直すと今度は荷主 ID が入る）。
DELETE FROM tracking_leg WHERE tracking_number IN (
    SELECT tracking_number FROM tracking_summary WHERE shipper_id IS NULL);
DELETE FROM tracking_event WHERE tracking_number IN (
    SELECT tracking_number FROM tracking_summary WHERE shipper_id IS NULL);
DELETE FROM tracking_summary WHERE shipper_id IS NULL;

-- ここから先は書き手が必ず入れる。入らなければ投影が落ちて気づける。
ALTER TABLE tracking_summary ALTER COLUMN shipper_id SET NOT NULL;
