-- migrate:up

-- IT8 US23 精算処理 - cargo.booking_status CHECK に 'Settled' を追加
--
-- あわせて既存 CHECK の潜在バグを修正する: 初版 CHECK には
-- 'RouteAssigned' (US11, IT4) と 'Cancelled' (US13, IT4) が含まれておらず、
-- 実装 (PostgresBookingRepository.bookingStatusToText) が書き込む値と
-- 乖離していた。BookingStatus sum type の全 8 状態に同期する。

ALTER TABLE cargo DROP CONSTRAINT cargo_booking_status_check;
ALTER TABLE cargo ADD CONSTRAINT cargo_booking_status_check
    CHECK (booking_status IN
        ('Draft', 'Submitted', 'RouteProposed', 'RouteAssigned',
         'Confirmed', 'Settled', 'Cancelled', 'Closed'));

-- migrate:down

ALTER TABLE cargo DROP CONSTRAINT cargo_booking_status_check;
ALTER TABLE cargo ADD CONSTRAINT cargo_booking_status_check
    CHECK (booking_status IN
        ('Draft', 'Submitted', 'RouteProposed', 'Confirmed', 'Closed'));
