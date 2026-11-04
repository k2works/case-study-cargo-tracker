-- 投影の待避は、(投影, 集約, 連番) ごとに 1 行。ストリームを読み直しても、同じイベントの待避が増えない（件数を監視する前提のため）。
CREATE UNIQUE INDEX dead_letter_event_source ON platform.dead_letter_event (projection_name, persistence_id, seq_nr);
