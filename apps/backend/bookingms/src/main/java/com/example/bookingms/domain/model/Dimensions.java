package com.example.bookingms.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** 貨物の外寸（cm）。3 辺そろって初めて意味を持つため、まとめて扱う。 */
public final class Dimensions {

    private final BigDecimal lengthCm;
    private final BigDecimal widthCm;
    private final BigDecimal heightCm;

    private Dimensions(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
    }

    public static Dimensions of(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        requirePositive(lengthCm, "長さ");
        requirePositive(widthCm, "幅");
        requirePositive(heightCm, "高さ");
        return new Dimensions(lengthCm, widthCm, heightCm);
    }

    private static void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + "は 0 より大きい値で指定してください: " + value);
        }
    }

    public BigDecimal lengthCm() {
        return lengthCm;
    }

    public BigDecimal widthCm() {
        return widthCm;
    }

    public BigDecimal heightCm() {
        return heightCm;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Dimensions dimensions
                && lengthCm.compareTo(dimensions.lengthCm) == 0
                && widthCm.compareTo(dimensions.widthCm) == 0
                && heightCm.compareTo(dimensions.heightCm) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                lengthCm.stripTrailingZeros(), widthCm.stripTrailingZeros(),
                heightCm.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "%s × %s × %s cm".formatted(lengthCm, widthCm, heightCm);
    }
}
