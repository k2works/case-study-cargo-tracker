package com.example.cargotracker.bookingms.domain.model.valueobjects;

/**
 * 貨物種別。
 *
 * <ul>
 *   <li>{@link #GENERAL}: 一般貨物</li>
 *   <li>{@link #HAZARDOUS}: 危険物（{@link HazardInfo} 必須）</li>
 *   <li>{@link #REFRIGERATED}: 冷凍・冷蔵貨物（{@link TemperatureCondition} 必須）</li>
 * </ul>
 */
public enum CargoType {
    GENERAL,
    HAZARDOUS,
    REFRIGERATED
}
