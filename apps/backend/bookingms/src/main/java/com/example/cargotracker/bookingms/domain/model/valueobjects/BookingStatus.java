package com.example.cargotracker.bookingms.domain.model.valueobjects;

/**
 * 予約のライフサイクル状態。data-model.md と整合。
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
