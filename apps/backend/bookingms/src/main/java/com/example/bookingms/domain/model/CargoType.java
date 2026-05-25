package com.example.bookingms.domain.model;

/**
 * 貨物種別。
 *
 * <ul>
 *   <li>{@link #GENERAL} 一般貨物</li>
 *   <li>{@link #HAZARDOUS} 危険物（US05 で hazardInfo 必須）</li>
 *   <li>{@link #REFRIGERATED} 冷凍・冷蔵貨物（US05 で temperatureCondition 必須）</li>
 * </ul>
 */
public enum CargoType {
    GENERAL,
    HAZARDOUS,
    REFRIGERATED
}
