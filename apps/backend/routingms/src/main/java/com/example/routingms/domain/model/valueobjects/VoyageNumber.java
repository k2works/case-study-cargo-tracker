package com.example.routingms.domain.model.valueobjects;

import java.util.Objects;

public class VoyageNumber {

    private final String number;

    public VoyageNumber(String number) {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("VoyageNumber must not be blank");
        }
        this.number = number;
    }

    public String getNumber() {
        return number;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoyageNumber that)) return false;
        return Objects.equals(number, that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    @Override
    public String toString() {
        return number;
    }
}
