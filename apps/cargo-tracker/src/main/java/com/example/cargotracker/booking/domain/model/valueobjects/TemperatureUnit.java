package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 温度の単位（US05）。
 *
 * <p><strong>単位を持たない温度は指示にならない。</strong> -18 が摂氏か華氏かで
 * 貨物の状態はまったく違う。
 */
public enum TemperatureUnit {

    CELSIUS("℃", new java.math.BigDecimal("-273.15")),
    FAHRENHEIT("℉", new java.math.BigDecimal("-459.67"));

    private final String symbol;
    private final java.math.BigDecimal absoluteZero;

    TemperatureUnit(String symbol, java.math.BigDecimal absoluteZero) {
        this.symbol = symbol;
        this.absoluteZero = absoluteZero;
    }

    /**
     * この単位での絶対零度。
     *
     * <p><strong>単位ごとに違う。</strong> 摂氏の下限（-273.15）は華氏では
     * 有効な温度である。一律の下限で弾くと、正しい指定を拒む。
     */
    public java.math.BigDecimal absoluteZero() {
        return absoluteZero;
    }

    /** この単位で成り立たない温度か。 */
    public boolean isBelowAbsoluteZero(java.math.BigDecimal temperature) {
        return temperature.compareTo(absoluteZero) < 0;
    }

    /** 画面に出す記号。**列挙子名を利用者に見せない**。 */
    public String symbol() {
        return symbol;
    }
}
