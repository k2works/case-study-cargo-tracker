-- transport_status CHECK 制約追加（IT10 A4.2b / US33 / IT9 V5 バグ再発防止の横展開）
-- TransportStatus enum（9 値）と DB 値域を同期する。
--   tracking_summary.current_status: NOT NULL → enum 値必須
--   tracking_event.transport_status: NULL 許容 → NULL または enum 値
-- 既存データは Java 側 enum 変換を経由しているため必ず enum 値であり、
-- 制約追加による既存行の違反は発生しない（V2 でコメント記載の値域と同一）。
-- 同期検証は TransportStatusCheckConstraintTest で CI 担保する。

ALTER TABLE tracking_summary DROP CONSTRAINT IF EXISTS chk_tracking_summary_current_status;
ALTER TABLE tracking_summary
    ADD CONSTRAINT chk_tracking_summary_current_status CHECK (
        current_status IN ('NOT_RECEIVED', 'RECEIVED', 'LOADED', 'IN_TRANSIT',
                           'UNLOADED', 'AWAITING_CLAIM', 'DELIVERED',
                           'MISROUTED', 'EXCEPTION')
    );

ALTER TABLE tracking_event DROP CONSTRAINT IF EXISTS chk_tracking_event_transport_status;
ALTER TABLE tracking_event
    ADD CONSTRAINT chk_tracking_event_transport_status CHECK (
        transport_status IS NULL OR transport_status IN (
            'NOT_RECEIVED', 'RECEIVED', 'LOADED', 'IN_TRANSIT',
            'UNLOADED', 'AWAITING_CLAIM', 'DELIVERED',
            'MISROUTED', 'EXCEPTION'
        )
    );
