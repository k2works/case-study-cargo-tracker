-- 開発・デモ用の追跡サンプルデータ（繰り返し実行可能）
-- 本番環境では適用しない（IT3 の CI 構築時に profile で location を切り替える）

MERGE INTO tracking_activity (id, tracking_number, booking_id, transport_status) KEY (tracking_number) VALUES
    (1, 'TRK-20260803-0001', 'BK-0001', 'ONBOARD_CARRIER');

DELETE FROM tracking_handling_event WHERE tracking_id = 1;

INSERT INTO tracking_handling_event (tracking_id, event_type, event_time, location_unlocode, voyage_number) VALUES
    (1, 'RECEIVE', TIMESTAMP '2026-08-02 14:00:00', 'JPTYO', NULL),
    (1, 'LOAD',    TIMESTAMP '2026-08-03 09:15:00', 'JPTYO', 'V0042');
