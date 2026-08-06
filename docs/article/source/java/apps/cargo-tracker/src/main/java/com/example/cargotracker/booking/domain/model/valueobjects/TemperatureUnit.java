package com.example.cargotracker.booking.domain.model.valueobjects;

public enum TemperatureUnit {
    CELSIUS("℃"),
    FAHRENHEIT("℉");

    private final String displayName;

    TemperatureUnit(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
