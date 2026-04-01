package com.example.cargotracker.shipper.domain.model;

import java.util.Objects;

public final class ShipperName {

    private final String value;

    public ShipperName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("荷主名は null にできません");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("荷主名は空文字にできません");
        }
        if (value.length() > 200) {
            throw new IllegalArgumentException("荷主名は 200 文字以内にしてください");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShipperName that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
