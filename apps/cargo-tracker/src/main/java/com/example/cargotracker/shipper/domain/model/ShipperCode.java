package com.example.cargotracker.shipper.domain.model;

import java.util.regex.Pattern;

/**
 * 荷主コード。自動生成される業務識別コード。
 *
 * @param value {@code SHP-999999} 形式
 */
public record ShipperCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("SHP-\\d{6}");

    public ShipperCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("荷主コードは SHP-999999 形式です: " + value);
        }
    }

    /** 連番から荷主コードを生成する。 */
    public static ShipperCode of(long sequence) {
        return new ShipperCode("SHP-%06d".formatted(sequence));
    }
}
