package com.example.cargotracker.billing.domain.model.valueobjects;

public enum PaymentStatus {
    PENDING("未精算"),
    CONFIRMED("精算済"),
    OVERDUE("支払期限超過");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
