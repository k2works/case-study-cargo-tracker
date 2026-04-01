package com.example.cargotracker.booking.domain.model;

public enum CargoType {
    GENERAL_CARGO("一般貨物"),
    DANGEROUS_GOODS("危険物"),
    REFRIGERATED("冷凍・冷蔵");

    private final String displayName;

    CargoType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
