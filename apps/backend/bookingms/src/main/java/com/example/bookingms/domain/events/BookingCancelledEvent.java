package com.example.bookingms.domain.events;

/**
 * 予約キャンセルイベント（US13）。
 *
 * <p>予約状態が CANCELLED に遷移したことを表す。荷主へのキャンセル確認通知トリガーとなる。</p>
 */
public record BookingCancelledEvent(
        String bookingId,
        String bookingStatus
) {
}
