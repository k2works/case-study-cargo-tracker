package com.example.cargotracker.booking.domain.model;

/**
 * 温度の単位（US05）。
 *
 * <p><strong>単位を持たない温度は指示にならない。</strong> -18 が摂氏か華氏かで
 * 貨物の状態はまったく違う。
 */
public enum TemperatureUnit {

    CELSIUS("℃"),
    FAHRENHEIT("℉");

    private final String symbol;

    TemperatureUnit(String symbol) {
        this.symbol = symbol;
    }

    /** 画面に出す記号。**列挙子名を利用者に見せない**。 */
    public String symbol() {
        return symbol;
    }
}
