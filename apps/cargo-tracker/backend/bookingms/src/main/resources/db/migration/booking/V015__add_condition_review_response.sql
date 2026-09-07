-- 荷主との協議の結果（US10 §受入基準 4 の対 / IT8 H.2）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「cargo_summary」
--
-- **差し戻しの記録は消さない。** 何を頼まれて何が決まったかが対で読めないと、
-- 経路設計者は条件をどう直せばよいのか分からない。
--
-- 営業の受け皿（S02）は「差し戻されていて、まだ返していない」で絞る。
-- 返したものが残り続けると、営業は何度も同じ予約を開く。

ALTER TABLE cargo_summary ADD COLUMN condition_review_response TEXT;
ALTER TABLE cargo_summary ADD COLUMN condition_review_responded_at TIMESTAMPTZ;
