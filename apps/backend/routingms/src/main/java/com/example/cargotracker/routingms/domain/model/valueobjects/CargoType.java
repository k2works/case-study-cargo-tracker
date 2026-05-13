package com.example.cargotracker.routingms.domain.model.valueobjects;

/**
 * 対応貨物種別。bookingms の同名 enum と整合（将来 shared モジュールに統合候補）。
 */
public enum CargoType {
    GENERAL,
    HAZARDOUS,
    REFRIGERATED
}
