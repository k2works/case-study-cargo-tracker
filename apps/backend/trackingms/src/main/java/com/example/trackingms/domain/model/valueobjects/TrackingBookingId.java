package com.example.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 追跡コンテキストの予約 ID（値オブジェクト）
 */
public record TrackingBookingId(String bookingId) {
    public TrackingBookingId {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        if (bookingId.isBlank()) {
            throw new IllegalArgumentException("bookingId must not be blank");
        }
    }

    @Override
    public String toString() {
        return bookingId;
    }
}
