package com.example.cargotracker.tracking.domain.model;

import java.util.UUID;

/**
 * 予約参照 ID（Tracking Context 固有の型）。
 *
 * <p>Booking の {@code BookingId} を参照しない（ADR-005・ArchUnit ルール 4）。
 * 追跡が知る必要があるのは「どの予約の追跡か」という事実だけである。
 *
 * @param value 予約 ID
 */
public record TrackingBookingId(UUID value) {

    public TrackingBookingId {
        if (value == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
    }
}
