package com.example.cargotracker.tracking.domain.model.valueobjects;

public enum TrackingEventType {
    RECEIVE("受領"),
    LOAD("積込"),
    UNLOAD("荷降し"),
    CLAIM("引取"),
    MANUAL_UPDATE("手動更新"),
    EXCEPTION("例外発生");

    private final String displayName;

    TrackingEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
