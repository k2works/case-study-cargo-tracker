package com.example.cargotracker.shipper.domain.model.valueobjects;

public record ContractNumber(String value) {

    public ContractNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("contractNumber must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
