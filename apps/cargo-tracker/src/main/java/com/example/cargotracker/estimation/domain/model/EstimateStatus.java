package com.example.cargotracker.estimation.domain.model;

public enum EstimateStatus {
    CREATED("作成済"),
    EXPIRED("期限切れ");

    private final String displayName;

    EstimateStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
