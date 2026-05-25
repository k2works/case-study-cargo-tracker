package com.example.bookingms.domain.model;

/**
 * 荷主種別。
 *
 * <ul>
 *   <li>{@link #INDIVIDUAL} 個人荷主</li>
 *   <li>{@link #CORPORATE} 法人荷主（US03 で契約番号・割引率を追加）</li>
 * </ul>
 */
public enum ShipperType {
    INDIVIDUAL,
    CORPORATE
}
