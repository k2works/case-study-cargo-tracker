package com.example.cargotracker.booking.domain.model.valueobjects;

public enum BookingStatus {
    PROVISIONAL("仮予約"),
    CONFIRMED("確定済"),
    SETTLED("精算済");

    private final String displayName;

    BookingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
