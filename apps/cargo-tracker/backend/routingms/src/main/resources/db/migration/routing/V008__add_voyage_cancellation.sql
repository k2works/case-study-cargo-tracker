-- 航海のキャンセル（US24 / IT5 R.1）。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- cancelled は V002 から在るが、いつ・なぜ・誰が止めたかは持っていなかった。
-- 理由を記録しても読み口が無ければ誰にも見えないので、投影に 3 列を置き、
-- 航海詳細（S34）に出す。変更の履歴は Event Store が持つ。
ALTER TABLE voyage ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE voyage ADD COLUMN cancel_reason VARCHAR(200);
ALTER TABLE voyage ADD COLUMN cancelled_by VARCHAR(50);
