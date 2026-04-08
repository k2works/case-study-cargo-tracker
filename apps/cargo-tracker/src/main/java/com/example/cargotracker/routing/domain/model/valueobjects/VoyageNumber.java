package com.example.cargotracker.routing.domain.model.valueobjects;

import java.util.Objects;

public record VoyageNumber(String number) {

    public VoyageNumber {
        Objects.requireNonNull(number, "voyageNumber must not be null");
        if (number.isBlank()) {
            throw new IllegalArgumentException("voyageNumber must not be blank");
        }
    }

    @Override
    public String toString() {
        return number;
    }
}
