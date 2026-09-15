-- 連鎖の待ちを別の列で持つ（IT16 のレビュー N8）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（simulation_read_db）
--
-- **所要時間が API の呼び出しだけを数えていた。** 1 本のシナリオは連鎖の待ちを
-- 含めて数十秒かかるのに、画面には「13 工程が数ミリ秒ずつ」と出ていた——
-- 読む人は「速い」と読み、実際に時間を使っている場所が見えない。
--
-- **足し合わせて 1 つにしない。** 呼び出しが遅いのか連鎖が遅いのかは、
-- 切り分けるときにいちばん知りたい区別である。
ALTER TABLE simulation_step
    ADD COLUMN waited_ms BIGINT;

COMMENT ON COLUMN simulation_step.waited_ms IS
    '連鎖の結果が読めるようになるまで待った時間（ミリ秒）。elapsed_ms は API の呼び出しだけ';
