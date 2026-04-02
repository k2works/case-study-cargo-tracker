package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 追跡 BC 内で使用するイベント種別。
 * handling BC の {@code HandlingEventType} に直接依存しないよう BC 境界内に定義する。
 */
public enum TrackingEventType {
    LOAD("積み込み"),
    UNLOAD("荷降ろし"),
    CUSTOMS("通関"),
    TRANSHIP("積み替え"),
    RECEIVE("引取"),
    MANUAL_UPDATE("手動更新");

    private final String displayName;

    TrackingEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
