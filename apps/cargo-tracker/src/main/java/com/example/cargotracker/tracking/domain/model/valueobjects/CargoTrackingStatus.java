package com.example.cargotracker.tracking.domain.model.valueobjects;

public enum CargoTrackingStatus {
    AWAITING_RECEIPT("受領待ち"),
    RECEIVED("受領済"),
    LOADED("積込済"),
    UNLOADED("荷降し済"),
    CLAIMED("引取済"),
    DELIVERED("配達完了");

    private final String displayName;

    CargoTrackingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
