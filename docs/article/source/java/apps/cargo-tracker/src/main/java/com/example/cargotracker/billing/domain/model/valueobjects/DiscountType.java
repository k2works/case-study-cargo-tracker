package com.example.cargotracker.billing.domain.model.valueobjects;

public enum DiscountType {
    NONE("割引なし"),
    CORPORATE("法人割引");

    private final String displayName;

    DiscountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
