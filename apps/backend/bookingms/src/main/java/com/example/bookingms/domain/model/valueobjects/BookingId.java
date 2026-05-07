package com.example.bookingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 予約 ID 値オブジェクト
 */
public class BookingId {

    private final String id;

    public BookingId(String id) {
        Objects.requireNonNull(id, "booking id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("booking id must not be blank");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookingId bookingId = (BookingId) o;
        return Objects.equals(id, bookingId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
