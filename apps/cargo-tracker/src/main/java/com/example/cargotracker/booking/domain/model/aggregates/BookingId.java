package com.example.cargotracker.booking.domain.model.aggregates;

import java.util.Objects;
import java.util.UUID;

public final class BookingId {

    private final UUID value;

    public BookingId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("BookingId の値は null にできません");
        }
        this.value = value;
    }

    public static BookingId generate() {
        return new BookingId(UUID.randomUUID());
    }

    public static BookingId of(String value) {
        return new BookingId(UUID.fromString(value));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookingId that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
