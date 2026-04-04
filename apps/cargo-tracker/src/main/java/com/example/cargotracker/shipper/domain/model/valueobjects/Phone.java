package com.example.cargotracker.shipper.domain.model.valueobjects;

import java.util.Objects;

public final class Phone {

    private final String value;

    public Phone(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Phone phone)) {
            return false;
        }
        return Objects.equals(value, phone.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
