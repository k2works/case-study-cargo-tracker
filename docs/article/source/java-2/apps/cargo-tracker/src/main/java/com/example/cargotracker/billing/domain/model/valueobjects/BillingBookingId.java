package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 予約への参照（US21）。
 *
 * <p><strong>Booking の {@code BookingId} を共有しない</strong>（ADR-005）。
 * BC をまたいで運べるのは素の値だけである。
 *
 * @param value 予約 ID（UUID の文字列表現）
 */
public record BillingBookingId(String value) {

    public BillingBookingId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        value = value.strip();
    }
}
