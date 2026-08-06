package com.example.cargotracker.booking.domain.model.aggregates;

public enum CargoType {
    GENERAL("一般"),
    HAZARDOUS("危険物"),
    REFRIGERATED("冷凍・冷蔵");

    private final String displayName;

    CargoType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
