package com.example.cargotracker.booking.domain.repository;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.aggregates.BookingNotification;
import java.util.List;

/** 通知の送信記録（US12）。 */
public interface BookingNotificationRepository {

    /** 記録を残す。**送信の成否によらず残す。** */
    BookingNotification save(BookingNotification notification);

    /** 予約の通知履歴を新しい順に返す。 */
    List<BookingNotification> findByBookingId(BookingId bookingId);
}
