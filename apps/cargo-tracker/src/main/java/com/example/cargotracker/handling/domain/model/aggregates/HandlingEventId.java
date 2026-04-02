package com.example.cargotracker.handling.domain.model.aggregates;

import java.util.Objects;
import java.util.UUID;

/**
 * 荷役イベント ID 値オブジェクト。
 */
public final class HandlingEventId {

    private final UUID value;

    public HandlingEventId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("HandlingEventId の値は null にできません");
        }
        this.value = value;
    }

    public static HandlingEventId generate() {
        return new HandlingEventId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HandlingEventId that)) return false;
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
