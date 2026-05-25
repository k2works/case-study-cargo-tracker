package com.example.bookingms.domain.model;

/**
 * 予約業務状態。
 *
 * <p>US04 で PRELIMINARY が初期状態。後続ストーリーで他状態へ遷移。</p>
 */
public enum BookingStatus {
    PRELIMINARY,
    ROUTING,
    ROUTE_PROPOSED,
    CONFIRMED,
    TRACKING_ISSUED,
    IN_TRANSIT,
    DELIVERED,
    SETTLED,
    CANCELLED
}
