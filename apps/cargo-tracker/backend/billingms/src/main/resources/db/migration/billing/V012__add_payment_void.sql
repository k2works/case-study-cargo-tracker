-- 記録した入金の取り消し（UC18 / US23。IT15 引き継ぎ 3）。
--
-- **行は消さない。** 消すと「誤って記録して取り消した」事実そのものが残らない。
-- 印を付けて、入金として数えないだけにする（`invoice` の取消と同じ考え方）。
--
-- **既存の行は取り消されていない。** 列が無かったころの入金は NULL のままで、
-- そのまま有効な入金として読める（不変条件の追加で既存行を読めなくしない）。
ALTER TABLE payment ADD COLUMN voided_at   TIMESTAMPTZ;
ALTER TABLE payment ADD COLUMN voided_by   VARCHAR(50);
ALTER TABLE payment ADD COLUMN void_reason TEXT;
