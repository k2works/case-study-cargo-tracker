package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 輸送料金の算出ステータス。
 */
public enum ChargeStatus {
    DRAFT("算出中"),
    CONFIRMED("確定");

    private final String displayName;

    ChargeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
