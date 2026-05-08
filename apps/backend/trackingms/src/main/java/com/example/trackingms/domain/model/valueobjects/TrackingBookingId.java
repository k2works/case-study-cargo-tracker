package com.example.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 追跡コンテキストの予約 ID（値オブジェクト）
 */
public final class TrackingBookingId {
    private final String bookingId;

    public TrackingBookingId(String bookingId) {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        if (bookingId.isBlank()) {
            throw new IllegalArgumentException("bookingId must not be blank");
        }
        this.bookingId = bookingId;
    }

    public String getBookingId() {
        return bookingId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackingBookingId that = (TrackingBookingId) o;
        return Objects.equals(bookingId, that.bookingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookingId);
    }

    @Override
    public String toString() {
        return bookingId;
    }
}
