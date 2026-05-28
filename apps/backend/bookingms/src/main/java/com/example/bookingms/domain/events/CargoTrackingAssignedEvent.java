package com.example.bookingms.domain.events;

/**
 * 貨物への追跡情報割当イベント（US14、bookingms ローカル）。
 *
 * <p>{@code AssignTrackingDetailsCommand} の処理で発行され、予約状態を CONFIRMED から
 * TRACKING_ISSUED へ遷移し、採番された追跡番号を保持する。Read Model
 * （cargo_summary の tracking_number 列と booking_status の更新）の更新トリガーとなる。</p>
 *
 * <p>shared モジュールの {@code com.example.shared.events.CargoTrackedEvent}（cross-service
 * trackingms → bookingms）と区別するため、ローカルイベントは {@code CargoTrackingAssignedEvent}
 * という別名を採用する。</p>
 *
 * @param bookingId      予約識別子
 * @param trackingNumber 採番済み追跡番号（TRK- + 大文字英数 10 桁）
 * @param bookingStatus  遷移後の予約状態（通常は {@code "TRACKING_ISSUED"}）
 */
public record CargoTrackingAssignedEvent(
        String bookingId,
        String trackingNumber,
        String bookingStatus
) {
}
