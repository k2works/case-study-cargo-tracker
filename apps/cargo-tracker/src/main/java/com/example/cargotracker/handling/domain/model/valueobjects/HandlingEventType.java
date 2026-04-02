package com.example.cargotracker.handling.domain.model.valueobjects;

/**
 * 荷役イベント種別。
 */
public enum HandlingEventType {
    /** 積み込み */
    LOAD("積み込み"),
    /** 荷降ろし */
    UNLOAD("荷降ろし"),
    /** 通関 */
    CUSTOMS("通関"),
    /** 積み替え */
    TRANSHIP("積み替え"),
    /** 引取 */
    RECEIVE("引取"),
    /** 手動更新 — システム自動連携外の訂正・補正イベントを後から記録する場合に使用 */
    MANUAL_UPDATE("手動更新");

    private final String displayName;

    HandlingEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
