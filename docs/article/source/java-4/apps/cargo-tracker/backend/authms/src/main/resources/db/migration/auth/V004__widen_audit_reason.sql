-- 監査ログの理由を広げる（US18・US23・US37 の前提となる荷主の紐付け。IT17）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（auth_db）
--
-- **識別子は列の長さに収める。** 荷主の紐付けを記録するとき、理由の欄に
-- 荷主 ID（UUID は 36 文字）を入れて 30 文字にあふれた——**この形はこの
-- プロジェクトで 4 度目**である（`PAY-` + UUID、`SIM-` + UUID、`run_id`）。
--
-- **切り詰めない。** 「どの荷主に紐付けたか」が読めなければ、監査の意味が無い
-- ——静かに効く操作こそ、あとから誰が何をしたか辿れなければならない。
--
-- **既存の行は壊れない**（広げるだけ）。
ALTER TABLE auth_audit_log
    ALTER COLUMN reason TYPE VARCHAR(64);

COMMENT ON COLUMN auth_audit_log.reason IS
    '事象の補足（ロックの理由・紐付けた荷主 ID など）。識別子が入るので 64 文字';
