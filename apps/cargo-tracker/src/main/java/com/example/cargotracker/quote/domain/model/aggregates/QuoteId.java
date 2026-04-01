package com.example.cargotracker.quote.domain.model.aggregates;

import java.util.Objects;
import java.util.UUID;

/**
 * Quote 集約の識別子。
 */
public final class QuoteId {

    private final UUID value;

    public QuoteId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("QuoteId の値は null にできません");
        }
        this.value = value;
    }

    public static QuoteId generate() {
        return new QuoteId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteId that)) return false;
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
