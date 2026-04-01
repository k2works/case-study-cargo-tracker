package com.example.cargotracker.routing.domain.model;

/**
 * routing コンテキストにおける貨物種別。
 */
public enum CargoType {
    /** 一般貨物 */
    GENERAL,
    /** 危険物 */
    HAZARDOUS,
    /** 冷凍貨物 */
    REFRIGERATED
}
