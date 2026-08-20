package com.example.bookingms.domain.model;

/** 貨物種別。種別ごとに必要な追加情報が変わる（US05）。 */
public enum CargoType {
    /** 一般貨物。危険物申告も温度条件も持たない。 */
    GENERAL,
    /** 危険物。危険物申告が必須。 */
    HAZARDOUS,
    /** 冷凍・冷蔵貨物。温度条件が必須。 */
    REFRIGERATED
}
