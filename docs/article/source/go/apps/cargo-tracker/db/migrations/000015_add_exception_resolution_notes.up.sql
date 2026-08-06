-- Tracking Context: 例外の対応内容メモと発生場所（US19/US20・IT7 注2）。
-- resolution_notes は data-model 定義済みだが migration 000013 で欠落。
-- location_unlocode は domain の TrackingExceptionEvent.location を永続化するため追加。
ALTER TABLE tracking_exception_event
    ADD COLUMN resolution_notes  TEXT,
    ADD COLUMN location_unlocode VARCHAR(5);
