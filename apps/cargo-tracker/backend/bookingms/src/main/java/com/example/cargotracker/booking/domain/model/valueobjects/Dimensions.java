package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * 寸法（cm）。
 *
 * <p>US04 §受入基準 2 が「寸法」を求める。集約が持っていても投影と画面が落とすと
 * 受入基準を満たせないので、`cargo_summary` にも 3 列を持つ。</p>
 */
public record Dimensions(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {

    public Dimensions {
        require(lengthCm, "長さ");
        require(widthCm, "幅");
        require(heightCm, "高さ");
    }

    private static void require(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + "は 0 より大きい値です: " + value);
        }
    }

    public static Dimensions of(String length, String width, String height) {
        return new Dimensions(new BigDecimal(length), new BigDecimal(width),
                new BigDecimal(height));
    }
}
