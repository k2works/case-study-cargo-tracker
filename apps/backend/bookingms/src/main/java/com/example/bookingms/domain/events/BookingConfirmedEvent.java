package com.example.bookingms.domain.events;

/**
 * 予約確定イベント（US13）。
 *
 * <p>予約状態が CONFIRMED に遷移したことを表す。追跡番号発行依頼（IT5）の Saga トリガーとなる。</p>
 */
public record BookingConfirmedEvent(
        String bookingId,
        String bookingStatus
) {
}
