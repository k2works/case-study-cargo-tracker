package com.example.cargotracker.booking.domain.model.valueobjects;

/** 貨物種別（domain-model.md「ユビキタス言語」）。 */
public enum CargoType {
    /** 一般。 */
    GENERAL,
    /** 危険物。危険物申告が要る。 */
    HAZARDOUS,
    /** 冷凍・冷蔵。温度管理条件が要る。 */
    REFRIGERATED
}
