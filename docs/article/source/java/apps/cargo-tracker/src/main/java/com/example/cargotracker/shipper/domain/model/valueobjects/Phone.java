package com.example.cargotracker.shipper.domain.model.valueobjects;

public record Phone(String value) {

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
