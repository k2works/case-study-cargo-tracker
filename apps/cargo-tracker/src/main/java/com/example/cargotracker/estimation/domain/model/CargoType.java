package com.example.cargotracker.estimation.domain.model;

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
