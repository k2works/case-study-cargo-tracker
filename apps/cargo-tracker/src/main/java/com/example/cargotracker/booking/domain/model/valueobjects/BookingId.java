package com.example.cargotracker.booking.domain.model.valueobjects;

import java.util.UUID;

/**
 * 予約識別子。
 *
 * @param value UUID
 */
public record BookingId(UUID value) {

    public BookingId {
        if (value == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
    }

    public static BookingId generate() {
        return new BookingId(UUID.randomUUID());
    }

    public static BookingId of(String value) {
        return new BookingId(UUID.fromString(value));
    }
}
