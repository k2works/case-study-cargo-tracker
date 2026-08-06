package com.example.cargotracker.tracking.domain.model.valueobjects;

public enum ExceptionType {
    DELAY("遅延", false),
    DAMAGE("破損", false),
    LOST("紛失", true);

    private final String displayName;
    private final boolean escalationRequired;

    ExceptionType(String displayName, boolean escalationRequired) {
        this.displayName = displayName;
        this.escalationRequired = escalationRequired;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEscalationRequired() {
        return escalationRequired;
    }
}
