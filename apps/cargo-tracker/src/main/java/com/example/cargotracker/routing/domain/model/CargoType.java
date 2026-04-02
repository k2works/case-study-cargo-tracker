package com.example.cargotracker.routing.domain.model;

/**
 * routing コンテキストにおける貨物種別。
 */
public enum CargoType {
    /** 一般貨物 */
    GENERAL("一般貨物"),
    /** 危険物 */
    HAZARDOUS("危険物"),
    /** 冷凍貨物 */
    REFRIGERATED("冷凍・冷蔵");

    private final String displayName;

    CargoType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
