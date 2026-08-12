package com.example.cargotracker.routing.domain.model.aggregates;

import java.util.UUID;

/**
 * Routing Context が扱う予約 ID。
 *
 * <p><strong>Booking の {@code BookingId} を参照しない。</strong> 値は同じ UUID だが、
 * BC をまたいで型を共有すると片方の都合が他方に伝わる（ADR-005・ArchUnit ルール 4）。
 * 共有カーネルに上げる案も採らない。共有カーネルは {@code Location} と
 * {@code ShipperId} の 2 要素に限る。
 *
 * @param value 予約 ID
 */
public record RoutingBookingId(UUID value) {

    public RoutingBookingId {
        if (value == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
    }

    /** 文字列から作る。形式が不正なら拒否する。 */
    public static RoutingBookingId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        try {
            return new RoutingBookingId(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("予約 ID の形式が不正です: " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
