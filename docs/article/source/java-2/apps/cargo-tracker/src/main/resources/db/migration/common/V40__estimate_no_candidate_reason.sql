-- 候補が 0 件だった理由を残す（US01 の受入基準 5。ADR-023）。
--
-- **「便が無い」と「期限に間に合わない」は別の事態である。** 営業担当者が次に
-- 取る行動が違う —— 前者は別の港を提案するか他社を当たること、後者は荷主に
-- 期限の延長を相談することである。まとめて「候補がありません」と出すと、
-- どちらなのか分からない。
--
-- **理由は作成時に決まり、あとから変わらない。** 便はあとで追加されうるが、
-- 荷主に伝えたのは作成時点の話である。候補そのものを写しで持つのと同じ理由で、
-- 理由も写しで持つ。
--
-- NO_VOYAGE   : その区間を走る便が無かった
-- DEADLINE    : 便はあったが希望期限に間に合わなかった
-- NULL        : 候補がある（理由は要らない）
ALTER TABLE estimate
    ADD COLUMN no_candidate_reason VARCHAR(20);

ALTER TABLE estimate
    ADD CONSTRAINT chk_estimate_no_candidate_reason
    CHECK (no_candidate_reason IS NULL
           OR no_candidate_reason IN ('NO_VOYAGE', 'DEADLINE'));
