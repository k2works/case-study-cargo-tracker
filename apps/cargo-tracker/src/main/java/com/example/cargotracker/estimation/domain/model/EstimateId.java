package com.example.cargotracker.estimation.domain.model;

import java.util.UUID;

public record EstimateId(UUID value) {

    public EstimateId {
        if (value == null) {
            throw new IllegalArgumentException("estimateId must not be null");
        }
    }

    public static EstimateId generate() {
        return new EstimateId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
