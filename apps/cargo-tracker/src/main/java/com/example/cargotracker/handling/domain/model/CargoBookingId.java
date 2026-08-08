package com.example.cargotracker.handling.domain.model;

import java.util.UUID;

/**
 * 予約参照 ID（Handling モジュール固有の型）。
 *
 * <p>Booking の {@code BookingId} を参照しない（ADR-005・ArchUnit ルール 4）。
 *
 * @param value 予約 ID
 */
public record CargoBookingId(UUID value) {

    public CargoBookingId {
        if (value == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
    }
}
