package com.example.cargotracker.billing.domain.model.valueobjects;

public enum PaymentStatus {
    PENDING("支払い待ち"),
    CONFIRMED("支払い済み"),
    OVERDUE("支払い期限超過"),
    REFUNDED("返金済み");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
