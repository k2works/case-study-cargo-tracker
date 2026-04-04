package com.example.cargotracker.booking.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public final class BookingId {

    private final UUID value;

    public BookingId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("bookingId must not be null");
        }
        this.value = value;
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BookingId bookingId)) {
            return false;
        }
        return Objects.equals(value, bookingId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
