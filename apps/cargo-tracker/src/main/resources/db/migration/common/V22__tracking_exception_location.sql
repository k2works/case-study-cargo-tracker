-- 例外の発生場所（US19 / US20）と、解決報告の通知種別（US19）。
--
-- V1 で作った tracking_exception_event には **発生場所の列が無かった**。
-- 受入基準は「発生状況（場所・日時・理由）を記録できる」であり、
-- 場所が無いと「どこで起きたか」を残せない。IT9 の危険物 6 列とまったく同じ形
-- （テーブルはあるが列が足りない）であり、IT10 で C17 の検査を入れた動機である。
ALTER TABLE tracking_exception_event ADD COLUMN location_unlocode VARCHAR(5);

ALTER TABLE tracking_exception_event
    ADD CONSTRAINT fk_tracking_exception_location
        FOREIGN KEY (location_unlocode) REFERENCES location (unlocode);

-- **既存行のために NULL 可のままにする。** V1 の時点で起票された例外は
-- 場所を持ちようがない。新規の起票で必須にするのは集約の仕事であり、
-- ここで NOT NULL にすると、列が無かったころの行を読み戻せなくなる
-- （IT9 の CargoSpecification と同じ判断）。

-- 解決の報告は発生の通知と別種別で積む（US19「対応報告を送信できる」）。
-- 同じ種別にすると、荷主の通知履歴で「起きた」と「片づいた」を区別できない。
ALTER TABLE booking_notification DROP CONSTRAINT chk_notification_type;

ALTER TABLE booking_notification
    ADD CONSTRAINT chk_notification_type
        CHECK (notification_type IN ('ROUTE_CONFIRMED', 'SCHEDULE_CHANGED',
                                     'EXCEPTION_RAISED', 'EXCEPTION_RESOLVED',
                                     'STATUS_UPDATED'));
