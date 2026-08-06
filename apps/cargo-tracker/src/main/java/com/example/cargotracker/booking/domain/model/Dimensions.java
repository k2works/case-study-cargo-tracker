package com.example.cargotracker.booking.domain.model;

import java.math.BigDecimal;

/**
 * 貨物の寸法（センチメートル）。<strong>オプション項目</strong>。
 *
 * <p>オプションであることと、不正な値を受け入れることは別である。
 * 3 辺すべてが未入力なら「寸法なし」、一部だけ入力されている状態は
 * 入力の取りこぼしとして拒否する。**「未入力」と「不正」を同じ扱いにすると、
 * 入力途中のデータが寸法として保存される。**
 *
 * @param length 長さ（cm。正の値）
 * @param width  幅（cm。正の値）
 * @param height 高さ（cm。正の値）
 */
public record Dimensions(BigDecimal length, BigDecimal width, BigDecimal height) {

    private static final int SCALE = 3;

    public Dimensions {
        requirePositive(length, "長さ");
        requirePositive(width, "幅");
        requirePositive(height, "高さ");
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("寸法の" + name + "は必須です");
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("寸法の" + name + "は 0 より大きい値です: " + value);
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("寸法の" + name + "は小数第 3 位までです: " + value);
        }
    }

    public static Dimensions ofCentimeters(BigDecimal length, BigDecimal width, BigDecimal height) {
        return new Dimensions(length, width, height);
    }

    /**
     * 未入力を許して生成する。3 辺すべてが未入力なら {@code null}（寸法なし）を返す。
     *
     * @return 寸法。3 辺すべてが未入力なら {@code null}
     */
    public static Dimensions ofNullableCentimeters(
            BigDecimal length, BigDecimal width, BigDecimal height) {
        boolean allMissing = length == null && width == null && height == null;
        if (allMissing) {
            return null;
        }
        boolean anyMissing = length == null || width == null || height == null;
        if (anyMissing) {
            throw new IllegalArgumentException("寸法は縦・横・高さの 3 つをすべて入力してください");
        }
        return ofCentimeters(length, width, height);
    }
}
