package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 貨物予約の識別子（VARCHAR(36) UUID 文字列）。
 *
 * <p>data-model.md 命名規則「集約識別子は UUID 文字列（VARCHAR(36)）」に準拠。
 * bookingms の {@code BookingId} と同一書式。</p>
 */
public record BookingId(String value) {

    public BookingId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("BookingId は空にできません");
        }
        if (value.length() > 36) {
            throw new IllegalArgumentException("BookingId は 36 文字以下である必要があります");
        }
    }
}
